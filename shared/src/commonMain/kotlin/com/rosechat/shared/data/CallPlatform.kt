package com.rosechat.shared.data

// Мост между общим кодом и нативным WebRTC. На iPhone его реализует Swift-класс
// WebRTCCallEngine (iosApp/iosApp), на Windows движка нет — там кнопки звонка скрыты.
// Все колбэки движка приходят на главном потоке.
interface CallEngineListener {
    fun onLocalSdp(type: String, sdp: String)                                   // "offer" или "answer"
    fun onLocalCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String)
    fun onConnectionState(state: String)                                        // CONNECTED, COMPLETED, FAILED, DISCONNECTED, CLOSED, ...
    fun onError(message: String)
}

interface CallEngine {
    fun attach(listener: CallEngineListener)
    fun startCall(isVideo: Boolean, turnUsername: String, turnPassword: String)
    fun makeOffer()
    fun acceptOffer(sdp: String)
    fun applyAnswer(sdp: String)
    fun addCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String)
    fun mute(muted: Boolean)
    fun speaker(on: Boolean)
    fun setCameraOff(off: Boolean)
    fun switchCamera()
    fun stopCall()
}

object CallPlatform {
    var engine: CallEngine? = null
}
