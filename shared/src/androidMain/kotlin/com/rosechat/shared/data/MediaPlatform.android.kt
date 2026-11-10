package com.rosechat.shared.data

import androidx.compose.ui.graphics.ImageBitmap

// :app пока использует свои собственные, нативные реализации (FirebaseRepo/CloudinaryRepository/
// MediaSaver) — этот файл существует только чтобы commonMain компилировался и на androidTarget,
// см. тот же приём в GoogleSignIn.android.kt.

actual suspend fun pickFile(kind: MediaKind): PickedFile? =
    throw UnsupportedOperationException("На Android используется нативный выбор файла из :app")

actual suspend fun cloudinaryUpload(bytes: ByteArray, fileName: String, resourceType: String, mimeType: String): Result<String> =
    Result.failure(UnsupportedOperationException("На Android используется нативный CloudinaryRepository из :app"))

actual suspend fun downloadAndOpen(url: String, fileName: String): Result<Unit> =
    Result.failure(UnsupportedOperationException("На Android используется нативный MediaSaver из :app"))

actual suspend fun fetchBytes(url: String): Result<ByteArray> =
    Result.failure(UnsupportedOperationException("На Android используется нативный MediaSaver из :app"))

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? = null

actual val mediaStorageLabel: String = ""

actual fun voiceRecordingSupported(): Boolean = false
actual suspend fun startVoiceRecording(): Result<Unit> = Result.failure(UnsupportedOperationException("На Android запись голосовых делает :app"))
actual suspend fun stopVoiceRecording(): PickedFile? = null
actual fun cancelVoiceRecording() {}

actual fun videoNoteSupported(): Boolean = false
actual suspend fun recordVideoNote(): PickedFile? = null
