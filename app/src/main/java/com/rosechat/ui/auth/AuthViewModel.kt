package com.rosechat.ui.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthProvider
import com.rosechat.data.repository.FirebaseRepo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode { PHONE, EMAIL }
enum class AuthStep { INPUT, OTP, PICK_STICKER, DONE }

data class AuthState(
    val mode: AuthMode = AuthMode.PHONE,
    val step: AuthStep = AuthStep.INPUT,
    val phone: String = "", val otp: String = "", val verificationId: String = "",
    val email: String = "", val password: String = "", val confirmPassword: String = "",
    val displayName: String = "", val username: String = "",
    val isRegister: Boolean = true,
    val agreedToTerms: Boolean = false, val showTerms: Boolean = false,
    val isLoading: Boolean = false, val error: String? = null, val resetSent: Boolean = false
)

class AuthViewModel : ViewModel() {
    private val _s = MutableStateFlow(AuthState())
    val state = _s.asStateFlow()

    fun setMode(m: AuthMode) = _s.update { it.copy(mode = m, error = null, step = AuthStep.INPUT) }
    fun setRegister(v: Boolean) = _s.update { it.copy(isRegister = v, error = null) }
    fun onPhoneChanged(v: String) = _s.update { it.copy(phone = v.filter { c -> c.isDigit() || c == '+' }.take(13), error = null) }
    fun onOtpChanged(v: String) { val c = v.filter { it.isDigit() }.take(6); _s.update { it.copy(otp = c, error = null) }; if (c.length == 6) verifyOtp(c) }
    fun onEmailChanged(v: String) = _s.update { it.copy(email = v.trim(), error = null) }
    fun onPasswordChanged(v: String) = _s.update { it.copy(password = v, error = null) }
    fun onConfirmChanged(v: String) = _s.update { it.copy(confirmPassword = v, error = null) }
    fun onNameChanged(v: String) = _s.update { it.copy(displayName = v, error = null) }
    fun onUsernameChanged(v: String) = _s.update { it.copy(username = v.filter { c -> c.isLetterOrDigit() || c == '_' }.take(20), error = null) }
    fun setAgreedToTerms(v: Boolean) = _s.update { it.copy(agreedToTerms = v, error = null) }
    fun toggleTermsDialog(v: Boolean) = _s.update { it.copy(showTerms = v) }

    // Общая точка выхода из любого способа входа/регистрации — решает,
    // показывать ли выбор стикера (только для только что созданных аккаунтов)
    // или сразу пускать в приложение.
    private fun finishAuth(isNewUser: Boolean) {
        _s.update { it.copy(isLoading = false, step = if (isNewUser) AuthStep.PICK_STICKER else AuthStep.DONE) }
    }

    fun setProfileSticker(sticker: String) {
        val uid = FirebaseRepo.currentUid ?: return
        viewModelScope.launch {
            FirebaseRepo.updateUser(uid, mapOf("profileSticker" to sticker))
            _s.update { it.copy(step = AuthStep.DONE) }
        }
    }

    fun skipStickerPick() = _s.update { it.copy(step = AuthStep.DONE) }

    fun sendOtp(activity: Activity) {
        if (!_s.value.agreedToTerms) { _s.update { it.copy(error = "Нужно принять пользовательское соглашение") }; return }
        if (!_s.value.phone.matches(Regex("^\\+[0-9]{10,13}$"))) { _s.update { it.copy(error = "Введи номер: +7XXXXXXXXXX") }; return }
        _s.update { it.copy(isLoading = true, error = null) }
        FirebaseRepo.sendPhoneOtp(_s.value.phone, activity, object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(c: PhoneAuthCredential) = signInPhone(c)
            override fun onVerificationFailed(e: FirebaseException) = _s.update { it.copy(isLoading = false, error = e.message ?: "Ошибка") }
            override fun onCodeSent(vid: String, t: PhoneAuthProvider.ForceResendingToken) = _s.update { it.copy(isLoading = false, verificationId = vid, step = AuthStep.OTP) }
        })
    }

    fun verifyOtp(code: String = _s.value.otp) {
        val vid = _s.value.verificationId; if (vid.isEmpty()) { _s.update { it.copy(error = "Сначала запроси код") }; return }
        signInPhone(PhoneAuthProvider.getCredential(vid, code))
    }

    private fun signInPhone(credential: PhoneAuthCredential) {
        _s.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            FirebaseRepo.signInWithPhone(credential)
                .onSuccess { (_, isNew) -> finishAuth(isNew) }
                .onFailure { e -> _s.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun submitEmail() {
        val s = _s.value
        if (!s.email.contains("@")) { _s.update { it.copy(error = "Введи корректный email") }; return }
        if (s.password.length < 6) { _s.update { it.copy(error = "Пароль минимум 6 символов") }; return }
        if (s.isRegister) {
            if (s.confirmPassword != s.password) { _s.update { it.copy(error = "Пароли не совпадают") }; return }
            if (s.displayName.isBlank()) { _s.update { it.copy(error = "Введи своё имя") }; return }
            if (!s.agreedToTerms) { _s.update { it.copy(error = "Нужно принять пользовательское соглашение") }; return }
        }
        _s.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = if (s.isRegister) FirebaseRepo.registerEmail(s.email, s.password, s.displayName, s.username)
            else FirebaseRepo.signInEmail(s.email, s.password)
            result.onSuccess { (_, isNew) -> finishAuth(isNew) }
                .onFailure { e -> _s.update { it.copy(isLoading = false, error = friendly(e.message)) } }
        }
    }

    fun signInWithGoogle(idToken: String) {
        if (!_s.value.agreedToTerms) { _s.update { it.copy(error = "Нужно принять пользовательское соглашение") }; return }
        _s.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            FirebaseRepo.signInWithGoogle(idToken)
                .onSuccess { (_, isNew) -> finishAuth(isNew) }
                .onFailure { e -> _s.update { it.copy(isLoading = false, error = friendly(e.message)) } }
        }
    }

    fun googleSignInFailed(message: String?) {
        _s.update { it.copy(isLoading = false, error = message ?: "Не удалось войти через Google") }
    }

    fun resetPassword() {
        if (!_s.value.email.contains("@")) { _s.update { it.copy(error = "Введи email") }; return }
        viewModelScope.launch {
            FirebaseRepo.resetPassword(_s.value.email)
                .onSuccess { _s.update { it.copy(resetSent = true, error = null) } }
                .onFailure { e -> _s.update { it.copy(error = e.message) } }
        }
    }

    private fun friendly(msg: String?) = when {
        msg == null -> "Неизвестная ошибка"
        msg.contains("email-already-in-use") -> "Email уже зарегистрирован"
        msg.contains("user-not-found") -> "Пользователь не найден"
        msg.contains("wrong-password") || msg.contains("invalid-credential") -> "Неверный пароль"
        msg.contains("too-many-requests") -> "Слишком много попыток. Подожди."
        msg.contains("network") -> "Нет интернета"
        else -> msg
    }
}
