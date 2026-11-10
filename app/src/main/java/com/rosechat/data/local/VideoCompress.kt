package com.rosechat.data.local

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Обычное видео-сообщение (в отличие от кружка, который сразу пишется в нужном качестве
// через MediaRecorder — см. VideoNoteRecorder) собирают из уже готового файла в галерее,
// и почти любой такой файл сильно больше лимита документа Firestore в 1 МБ. Поэтому тут
// нужен настоящий транскод — распаковка исходных видео/аудио-дорожек через MediaCodec и
// перекодирование в тот же битрейт, что и у кружков (250 кбит/с видео + 32 кбит/с аудио),
// но без обрезки в квадрат — длинная сторона ужимается до MAX_SIDE, пропорции сохраняются.
//
// Видео-декодер пишет кадры НАПРЯМУЮ в Surface энкодера (createInputSurface) — так не
// нужно вручную работать с сырыми пикселями, и даунскейл до размера энкодера происходит
// автоматически на уровне буфера. Аудио-дорожка (если есть) декодируется в PCM и кодируется
// заново в AAC отдельным, более простым циклом без Surface.
//
// Первый заход в такого рода низкоуровневый код в проекте — протестировать на реальном
// устройстве нельзя, так что не исключены баги на конкретных форматах/кодеках исходников;
// как и со звонками и кружками, будет нужна итерация по факту тестов.
object VideoCompress {
    private const val TAG = "VideoCompress"
    // 480p/250кбит/с — это НЕ выбор по качеству, а обход лимита документа Firestore в 1 МБ
    // (Base64-путь). Когда настроен Cloudinary, файл в документе не лежит вообще, и держать
    // видео в таком качестве незачем — отсюда и жалоба "видео мыльное и звук плохой".
    // HQ-профиль ниже включается сам, когда Cloudinary настроен (см. ChatScreen.sendVideo).
    const val MAX_SIDE = 480
    const val MAX_SIDE_HQ = 720
    const val VIDEO_BITRATE = 250_000
    const val VIDEO_BITRATE_HQ = 2_500_000
    // 32 кбит/с был выбран не из соображений качества, а чтобы уложиться в 1 МБ документа
    // Firestore на Base64-пути (см. MAX_DURATION_MS ниже) — это и есть похоже, что все жалобы
    // на "искажённый"/"приглушённый" звук в видео: 32 кбит/с AAC объективно довольно низкое
    // качество само по себе, без какого-либо бага. Когда настроен Cloudinary (обычный случай),
    // этого ограничения по размеру больше нет — ChatScreen.sendVideo передаёт AUDIO_BITRATE_HQ
    // вместо этого дефолта. Дефолт остаётся прежним 32_000 только для Base64-фолбэка.
    private const val AUDIO_BITRATE = 32_000
    const val AUDIO_BITRATE_HQ = 128_000
    private const val TIMEOUT_US = 10_000L

    // 20с — (250+32 кбит/с) * 20с ≈ 705 КБ сырого mp4, после +33% на Base64 ≈ 918 КБ —
    // укладывается в 1 МБ документа с запасом ~130 КБ под остальные поля сообщения.
    const val MAX_DURATION_MS = 20_000L

    // Через Cloudinary лимита документа Firestore нет — держать 20с незачем.
    const val MAX_DURATION_MS_HQ = 180_000L

    fun maxDurationMs(hq: Boolean) = if (hq) MAX_DURATION_MS_HQ else MAX_DURATION_MS

    private data class Sample(val data: ByteArray, val flags: Int, val presentationTimeUs: Long)

    // Лёгкая проверка длительности без запуска кодеков — чтобы сразу отказать с понятным
    // сообщением, а не гонять тяжёлый транскод впустую.
    fun getDurationMs(context: Context, uri: Uri): Long? {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(context, uri)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            Log.e(TAG, "getDurationMs failed", e)
            null
        } finally {
            runCatching { mmr.release() }
        }
    }

    // Возвращает сырые байты итогового mp4 либо null при ошибке/если видео длиннее
    // MAX_DURATION_MS. Используется для загрузки в Cloudinary (см. CloudinaryRepository) —
    // там файл грузится как есть, Base64 не нужен.
    // Раньше вызывалась прямо из viewModelScope.launch{} (Main-поток по умолчанию) — то есть
    // весь MediaCodec-транскод (может занимать много секунд на 20-секундном ролике) выполнялся
    // прямо на UI-потоке. Это и есть причина "приложение не отвечает" после отправки видео —
    // Android показывает ANR, если Main-поток не отвечает дольше ~5с. Публичная suspend-обёртка
    // переносит работу на Dispatchers.Default (CPU-интенсивная, не блокирующий I/O — поэтому
    // Default, а не IO); сама логика — в отдельной обычной функции, чтобы её собственный
    // `return` не наткнулся на запрет non-local return из не-inline лямбды withContext.
    suspend fun compressToBytes(
        context: Context,
        uri: Uri,
        audioBitrate: Int = AUDIO_BITRATE,
        maxSide: Int = MAX_SIDE,
        videoBitrate: Int = VIDEO_BITRATE,
        maxDurationMs: Long = MAX_DURATION_MS,
        passthroughAudio: Boolean = false
    ): ByteArray? =
        withContext(Dispatchers.Default) { compressToBytesBlocking(context, uri, audioBitrate, maxSide, videoBitrate, maxDurationMs, passthroughAudio) }

    private fun compressToBytesBlocking(
        context: Context, uri: Uri, audioBitrate: Int, maxSide: Int, videoBitrate: Int, maxDurationMs: Long,
        passthroughAudio: Boolean
    ): ByteArray? {
        val outFile = File(context.cacheDir, "vc_${System.currentTimeMillis()}.mp4")
        return try {
            if (!transcode(context, uri, outFile, audioBitrate, maxSide, videoBitrate, maxDurationMs, passthroughAudio)) null
            else outFile.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "compressToBytes failed", e)
            null
        } finally {
            outFile.delete()
        }
    }

    // Base64-обёртка над compressToBytes — используется, когда Cloudinary не настроен
    // и видео по-прежнему едет прямо в документе Firestore (см. FirebaseRepo/ChatViewModel).
    suspend fun compressToBase64(context: Context, uri: Uri): String? =
        compressToBytes(context, uri)?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    private fun selectTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    private fun transcode(
        context: Context, uri: Uri, outFile: File, audioBitrate: Int,
        maxSide: Int, videoBitrate: Int, maxDurationMs: Long, passthroughAudio: Boolean
    ): Boolean {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return false
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        return try {
            pfd.use { p ->
                videoExtractor.setDataSource(p.fileDescriptor)
                audioExtractor.setDataSource(p.fileDescriptor)
            }

            val videoTrack = selectTrack(videoExtractor, "video/")
            if (videoTrack < 0) { Log.e(TAG, "no video track"); return false }
            val inputVideoFormat = videoExtractor.getTrackFormat(videoTrack)
            val mime = inputVideoFormat.getString(MediaFormat.KEY_MIME) ?: return false

            // MediaFormat.getLong(KEY_DURATION) — не факт, что доступен на всех уровнях API
            // из поддерживаемых проектом (minSdk 26), поэтому длительность берём тем же
            // способом, что и в sendVideo() (MediaMetadataRetriever), а не через экстрактор.
            val durationMs = getDurationMs(context, uri)
            if (durationMs != null && durationMs > maxDurationMs) { Log.w(TAG, "video too long: ${durationMs}ms"); return false }

            val srcW = inputVideoFormat.getInteger(MediaFormat.KEY_WIDTH)
            val srcH = inputVideoFormat.getInteger(MediaFormat.KEY_HEIGHT)
            // Поворот — это только метаданные контейнера (setOrientationHint ниже), сами
            // декодированные пиксели остаются в исходной, "неповёрнутой" ориентации сенсора.
            val rotation = if (inputVideoFormat.containsKey(MediaFormat.KEY_ROTATION)) inputVideoFormat.getInteger(MediaFormat.KEY_ROTATION) else 0
            val scale = (maxSide.toFloat() / maxOf(srcW, srcH)).coerceAtMost(1f)
            var outW = (srcW * scale).toInt().coerceAtLeast(2); outW -= outW % 2
            var outH = (srcH * scale).toInt().coerceAtLeast(2); outH -= outH % 2

            videoExtractor.selectTrack(videoTrack)
            val (videoFormat, videoSamples) = runVideoEncode(videoExtractor, videoTrack, mime, outW, outH, videoBitrate)
                ?: run { Log.e(TAG, "video encode failed"); return false }

            val audioTrack = selectTrack(audioExtractor, "audio/")
            var audioResult: Pair<MediaFormat, List<Sample>>? = null
            if (audioTrack >= 0) {
                audioExtractor.selectTrack(audioTrack)
                val audioMime = audioExtractor.getTrackFormat(audioTrack).getString(MediaFormat.KEY_MIME)
                // Если исходный звук уже AAC (а это почти любое видео с телефона) и нас не душит
                // лимит размера — не трогаем его вообще: копируем готовые сжатые пакеты как есть.
                // Никакого декодирования/перекодирования = никаких искажений, и бесплатно по времени.
                audioResult = if (passthroughAudio && audioMime == MediaFormat.MIMETYPE_AUDIO_AAC) {
                    readAudioPassthrough(audioExtractor, audioTrack)
                        ?: run {
                            // Не вышло прочитать как есть — откатываемся на перекодирование.
                            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                            runAudioEncode(audioExtractor, audioTrack, audioBitrate)
                        }
                } else {
                    runAudioEncode(audioExtractor, audioTrack, audioBitrate)
                }
            }

            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                if (rotation != 0) muxer.setOrientationHint(rotation)
                val muxVideoTrack = muxer.addTrack(videoFormat)
                val muxAudioTrack = audioResult?.let { muxer.addTrack(it.first) } ?: -1
                muxer.start()
                val info = MediaCodec.BufferInfo()
                // Пишем сэмплы вперемешку по времени, а не "всё видео, потом всё аудио": иначе в mp4
                // весь звук лежит одним куском в самом конце файла, и при воспроизведении по
                // ссылке плеер то и дело упирается в подгрузку аудио — это слышно как рваный/
                // заикающийся звук, даже если сама дорожка целая.
                val aSamples = audioResult?.second ?: emptyList()
                var vi = 0; var ai = 0
                while (vi < videoSamples.size || ai < aSamples.size) {
                    val takeVideo = when {
                        vi >= videoSamples.size -> false
                        ai >= aSamples.size || muxAudioTrack < 0 -> true
                        else -> videoSamples[vi].presentationTimeUs <= aSamples[ai].presentationTimeUs
                    }
                    if (takeVideo) writeSample(muxer, muxVideoTrack, videoSamples[vi++], info)
                    else writeSample(muxer, muxAudioTrack, aSamples[ai++], info)
                }
                muxer.stop()
            } finally {
                runCatching { muxer.release() }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "transcode failed", e)
            false
        } finally {
            runCatching { videoExtractor.release() }
            runCatching { audioExtractor.release() }
        }
    }

    private fun writeSample(muxer: MediaMuxer, track: Int, s: Sample, info: MediaCodec.BufferInfo) {
        val buf = ByteBuffer.wrap(s.data)
        info.set(0, s.data.size, s.presentationTimeUs, s.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv())
        muxer.writeSampleData(track, buf, info)
    }

    // Декодер пишет напрямую в Surface энкодера — простой конвейер "прочитали пакет из
    // экстрактора -> отдали декодеру -> декодер отрендерил кадр в Surface энкодера ->
    // забрали закодированный пакет у энкодера", без ручной работы с пикселями. Собираем
    // закодированные сэмплы в памяти (их тут совсем немного, целевой размер — сотни КБ),
    // а не пишем в MediaMuxer сразу — так не нужно синхронизировать старт муксера с
    // отдельной аудио-дорожкой (см. transcode()).
    private fun runVideoEncode(extractor: MediaExtractor, trackIndex: Int, mime: String, outW: Int, outH: Int, videoBitrate: Int): Pair<MediaFormat, List<Sample>>? {
        val inputFormat = extractor.getTrackFormat(trackIndex)
        val encFormat = MediaFormat.createVideoFormat(mime, outW, outH).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, 24)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val encoder = MediaCodec.createEncoderByType(mime)
        encoder.configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val inputSurface = encoder.createInputSurface()
        encoder.start()

        val decoder = MediaCodec.createDecoderByType(mime)
        decoder.configure(inputFormat, inputSurface, null, 0)
        decoder.start()

        return try {
            val info = MediaCodec.BufferInfo()
            val samples = mutableListOf<Sample>()
            var outputFormat: MediaFormat? = null
            var extractorDone = false
            var decoderEosSent = false
            var encoderDone = false

            while (!encoderDone) {
                if (!extractorDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decoderEosSent) {
                    val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (outIdx >= 0) {
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outIdx, info.size > 0) // true = отрендерить кадр в Surface энкодера
                        if (eos) { encoder.signalEndOfInputStream(); decoderEosSent = true }
                    }
                }

                var encOutIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                while (encOutIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    when {
                        encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = encoder.outputFormat
                        encOutIdx >= 0 -> {
                            if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                val data = encoder.getOutputBuffer(encOutIdx)!!
                                val arr = ByteArray(info.size)
                                data.position(info.offset); data.get(arr)
                                samples.add(Sample(arr, info.flags, info.presentationTimeUs))
                            }
                            val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            encoder.releaseOutputBuffer(encOutIdx, false)
                            if (eos) { encoderDone = true }
                        }
                    }
                    if (encoderDone) break
                    encOutIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                }
            }
            outputFormat?.let { it to samples }
        } finally {
            runCatching { decoder.stop() }; runCatching { decoder.release() }
            runCatching { encoder.stop() }; runCatching { encoder.release() }
            runCatching { inputSurface.release() }
        }
    }

    // Забирает уже готовые закодированные AAC-пакеты, не блокируясь надолго (короткий
    // таймаут) — вызывается, пока runAudioEncode ждёт свободный входной буфер энкодера,
    // чтобы вычитывание выхода двигало конвейер и входные буферы освобождались, а не
    // копился дедлок. Возвращает true, если энкодер уже отдал EOS (в норме — только
    // один раз, в самом конце потока).
    private fun drainAacEncoderOutput(encoder: MediaCodec, info: MediaCodec.BufferInfo, samples: MutableList<Sample>, setOutputFormat: (MediaFormat) -> Unit): Boolean {
        var sawEos = false
        var idx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
        while (idx != MediaCodec.INFO_TRY_AGAIN_LATER) {
            when {
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> setOutputFormat(encoder.outputFormat)
                idx >= 0 -> {
                    if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        val data = encoder.getOutputBuffer(idx)!!
                        val arr = ByteArray(info.size)
                        data.position(info.offset); data.get(arr)
                        samples.add(Sample(arr, info.flags, info.presentationTimeUs))
                    }
                    val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                    encoder.releaseOutputBuffer(idx, false)
                    if (eos) sawEos = true
                }
            }
            if (sawEos) break
            idx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
        }
        return sawEos
    }

    // Раньше это называлось "ограниченный по времени ретрай" только в комментарии — по факту
    // цикл `while (encInIdx < 0) { ... }` не проверял прошедшее время вообще, то есть был
    // способен крутиться бесконечно, если энкодер реально завис (завис бы весь transcode, а не
    // просто исказился звук). А в редком случае, когда drainAacEncoderOutput успевал поймать
    // EOS раньше, чем нашёлся свободный вход, PCM-кусок так и оставался пропущенным — то самое
    // выпадение звука, которое эта функция должна была устранить. Теперь: реальный дедлайн по
    // времени (SystemClock, не итерациям) и без early-break на EOS.
    private fun awaitEncoderInputBuffer(
        encoder: MediaCodec, info: MediaCodec.BufferInfo,
        samples: MutableList<Sample>, maxWaitMs: Long = 3000L,
        setOutputFormat: (MediaFormat) -> Unit
    ): Int {
        val deadline = SystemClock.elapsedRealtime() + maxWaitMs
        var idx = encoder.dequeueInputBuffer(TIMEOUT_US)
        while (idx < 0 && SystemClock.elapsedRealtime() < deadline) {
            drainAacEncoderOutput(encoder, info, samples, setOutputFormat)
            idx = encoder.dequeueInputBuffer(TIMEOUT_US)
        }
        return idx // -1, если реально не дождались за 3с — этот кусок пропустится с логом,
                    // но конвейер не подвиснет навсегда
    }

    // Читает уже сжатые AAC-пакеты дорожки один в один, без декодера и энкодера.
    private fun readAudioPassthrough(extractor: MediaExtractor, trackIndex: Int): Pair<MediaFormat, List<Sample>>? {
        return try {
            val format = extractor.getTrackFormat(trackIndex)
            val samples = mutableListOf<Sample>()
            val buf = ByteBuffer.allocate(256 * 1024)
            while (true) {
                buf.clear()
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                val arr = ByteArray(size)
                buf.position(0); buf.get(arr, 0, size)
                samples.add(Sample(arr, extractor.sampleFlags, extractor.sampleTime))
                extractor.advance()
            }
            if (samples.isEmpty()) null else format to samples
        } catch (e: Exception) {
            Log.e(TAG, "audio passthrough failed", e)
            null
        }
    }

    // Тот же принцип, что и у видео, но без Surface — декодер отдаёт PCM прямо в
    // ByteBuffer, который перекладывается во входной буфер AAC-энкодера.
    private fun runAudioEncode(extractor: MediaExtractor, trackIndex: Int, audioBitrate: Int): Pair<MediaFormat, List<Sample>>? {
        val inputFormat = extractor.getTrackFormat(trackIndex)
        val inMime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
        val sampleRate = if (inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
        val channelCount = if (inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val decoder = MediaCodec.createDecoderByType(inMime)
        decoder.configure(inputFormat, null, null, 0)
        decoder.start()

        fun makeEncoder(rate: Int, channels: Int): MediaCodec {
            val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, channels).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, audioBitrate)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            return enc
        }
        // Частота/каналы в заголовке дорожки — это НЕ всегда то, что реально отдаёт декодер
        // (HE-AAC, нестандартные исходники). Если энкодер настроен на одни параметры, а PCM
        // приходит с другими, звук выходит с неправильной скоростью/высотой и хрипит. Поэтому
        // на INFO_OUTPUT_FORMAT_CHANGED декодера сверяем реальные значения и при расхождении
        // пересоздаём энкодер (в него к этому моменту ещё не подали ни одного куска PCM).
        var encRate = sampleRate
        var encChannels = channelCount
        var encoder = makeEncoder(encRate, encChannels)

        return try {
            val info = MediaCodec.BufferInfo()
            val samples = mutableListOf<Sample>()
            var outputFormat: MediaFormat? = null
            var extractorDone = false
            var decoderDone = false
            var encoderDone = false

            while (!encoderDone) {
                if (!extractorDone) {
                    val inIdx = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = decoder.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                if (!decoderDone) {
                    val outIdx = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                    if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val of = decoder.outputFormat
                        val realRate = if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) of.getInteger(MediaFormat.KEY_SAMPLE_RATE) else encRate
                        val realCh = if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else encChannels
                        if ((realRate != encRate || realCh != encChannels) && samples.isEmpty()) {
                            Log.w(TAG, "decoder output ${realRate}Hz/${realCh}ch != header ${encRate}Hz/${encChannels}ch — recreating encoder")
                            runCatching { encoder.stop() }; runCatching { encoder.release() }
                            encRate = realRate; encChannels = realCh
                            encoder = makeEncoder(encRate, encChannels)
                        }
                    } else if (outIdx >= 0) {
                        val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        if (info.size > 0) {
                            // ВАЖНО (реальный баг, вызывавший "искажённый" звук): раньше тут
                            // был ОДИН вызов encoder.dequeueInputBuffer(TIMEOUT_US), и если
                            // именно в этот момент у AAC-энкодера не оказывалось свободного
                            // входного буфера (encInIdx < 0 — вполне обычная ситуация при
                            // конвейерной обработке), декодированный PCM-кусок просто
                            // выбрасывался: decoder.releaseOutputBuffer ниже вызывался в любом
                            // случае, а скопировать данные в энкодер было уже некуда. Каждая
                            // такая потеря — это выпавший кусочек звука, и на выходе весь трек
                            // звучал рвано/с щелчками, то есть "искажённо". Теперь копируем PCM
                            // из буфера декодера сразу (до releaseOutputBuffer), а свободный вход
                            // энкодера ждём в цикле, попутно вычитывая уже готовые закодированные
                            // пакеты — это освобождает входные буферы энкодера, если они
                            // придерживались из-за того, что мы не вычитывали выход.
                            val pcm = decoder.getOutputBuffer(outIdx)!!
                            pcm.position(info.offset); pcm.limit(info.offset + info.size)
                            val pcmCopy = ByteArray(info.size)
                            pcm.get(pcmCopy)
                            val pts = info.presentationTimeUs
                            decoder.releaseOutputBuffer(outIdx, false)

                            val encInIdx = awaitEncoderInputBuffer(encoder, info, samples) { outputFormat = it }
                            if (encInIdx >= 0) {
                                val encBuf = encoder.getInputBuffer(encInIdx)!!
                                if (pcmCopy.size > encBuf.capacity()) {
                                    // Защита от BufferOverflowException: если у какого-то декодера кусок
                                    // PCM окажется больше KEY_MAX_INPUT_SIZE энкодера (16384 байт) — раньше
                                    // encBuf.put() тут просто упал бы с необработанным исключением,
                                    // прерывая весь transcode. Обрезаем и логируем вместо падения; для
                                    // одного случайного куска это неслышимый щелчок, а не пропавшее видео.
                                    Log.w("VideoCompress", "PCM-кусок (${pcmCopy.size} байт) больше входного буфера энкодера (${encBuf.capacity()}) — обрезан")
                                    encBuf.clear(); encBuf.put(pcmCopy, 0, encBuf.capacity())
                                    encoder.queueInputBuffer(encInIdx, 0, encBuf.capacity(), pts, 0)
                                } else {
                                    encBuf.clear(); encBuf.put(pcmCopy)
                                    encoder.queueInputBuffer(encInIdx, 0, pcmCopy.size, pts, 0)
                                }
                            } else {
                                Log.w("VideoCompress", "Аудиоэнкодер не освободил вход за 3с — кусок звука пропущен")
                            }
                        } else {
                            decoder.releaseOutputBuffer(outIdx, false)
                        }
                        if (eos) {
                            val encInIdx = awaitEncoderInputBuffer(encoder, info, samples) { outputFormat = it }
                            if (encInIdx >= 0) {
                                encoder.queueInputBuffer(encInIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            } else {
                                Log.w("VideoCompress", "Не удалось отправить EOS в аудиоэнкодер за 3с")
                            }
                            decoderDone = true
                        }
                    }
                }

                var encOutIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                while (encOutIdx != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    when {
                        encOutIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = encoder.outputFormat
                        encOutIdx >= 0 -> {
                            if (info.size > 0 && (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                                val data = encoder.getOutputBuffer(encOutIdx)!!
                                val arr = ByteArray(info.size)
                                data.position(info.offset); data.get(arr)
                                samples.add(Sample(arr, info.flags, info.presentationTimeUs))
                            }
                            val eos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                            encoder.releaseOutputBuffer(encOutIdx, false)
                            if (eos) { encoderDone = true }
                        }
                    }
                    if (encoderDone) break
                    encOutIdx = encoder.dequeueOutputBuffer(info, TIMEOUT_US)
                }
            }
            outputFormat?.let { it to samples }
        } catch (e: Exception) {
            Log.e(TAG, "audio track transcode failed, continuing without audio", e)
            null
        } finally {
            runCatching { decoder.stop() }; runCatching { decoder.release() }
            runCatching { encoder.stop() }; runCatching { encoder.release() }
        }
    }
}
