package com.rosechat.shared.data

import androidx.compose.ui.graphics.ImageBitmap

// Три платформенные операции с медиа, нужные для отправки/получения фото-видео-файлов
// в чате: выбрать файл с диска (без записи с камеры/микрофона — на десктопе съёмки нет,
// только выбор уже готового файла), загрузить его в Cloudinary (тот же провайдер, что и
// на Android — тот же аккаунт guurqicg, тот же unsigned preset) и скачать+открыть уже
// полученный файл. Каждая реализована только на desktopMain (см. MediaPlatform.desktop.kt);
// androidMain получает заглушку — тот же приём, что и с googleSignIn() (GoogleSignIn.kt) —
// просто чтобы commonMain компилировался и на androidTarget тоже. :app эти функции никогда
// не вызывает — там свои, нативные (FirebaseRepo/CloudinaryRepository/MediaSaver).

enum class MediaKind { IMAGE, VIDEO, FILE }

data class PickedFile(val name: String, val bytes: ByteArray, val mimeType: String, val size: Long)

// null — человек закрыл диалог выбора файла, ничего не выбрав.
expect suspend fun pickFile(kind: MediaKind): PickedFile?

// resourceType — "image" | "video" | "raw" (см. CloudinaryRepository.kt на Android, та же схема).
expect suspend fun cloudinaryUpload(bytes: ByteArray, fileName: String, resourceType: String, mimeType: String): Result<String>

// Скачивает файл по ссылке в папку "Загрузки" пользователя и сразу открывает его
// приложением по умолчанию для этого типа файла (аналог MediaSaver.saveAndOpenFromUrl на Android).
expect suspend fun downloadAndOpen(url: String, fileName: String): Result<Unit>

// Просто скачать байты в память — для превью картинки прямо в пузыре сообщения, без
// сохранения на диск (это отдельно делает downloadAndOpen по кнопке "Скачать").
expect suspend fun fetchBytes(url: String): Result<ByteArray>

// Декодирует уже скачанные байты в ImageBitmap для отрисовки Image(...) в Compose —
// сам ImageBitmap общий тип для всех платформ, а вот как его получить из байтов —
// разное (BitmapFactory на Android, Skia/loadImageBitmap на десктопе).
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

// Человекочитаемая строка "куда сейчас грузятся медиа" — для экрана настроек
// (см. App.kt → SettingsScreen). На Android не используется (там свой SettingsScreen).
expect val mediaStorageLabel: String

// ── Запись голосовых сообщений ───────────────────────────────────────────────────────────
// Реализована на iPhone (AVAudioRecorder → .m4a). На остальных платформах false/заглушки.
expect fun voiceRecordingSupported(): Boolean

// Запрашивает доступ к микрофону (если ещё не давали) и начинает запись.
expect suspend fun startVoiceRecording(): Result<Unit>

// Останавливает запись и возвращает файл; null — записи нет или она короче ~0.7 с.
expect suspend fun stopVoiceRecording(): PickedFile?

// Бросает текущую запись без отправки (отмена, выход из чата).
expect fun cancelVoiceRecording()

// ── Видео-кружки ─────────────────────────────────────────────────────────────────────────
// Съёмка короткого видео фронтальной камерой (до 60 с). Реализована на iPhone через системную
// камеру; на остальных платформах false/заглушка. null — человек закрыл камеру.
expect fun videoNoteSupported(): Boolean
expect suspend fun recordVideoNote(): PickedFile?
