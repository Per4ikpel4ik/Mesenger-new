import Foundation
import WebRTC

// Общая фабрика WebRTC для всего приложения (создаётся один раз).
enum WebRTCBootstrap {
    static let factory: RTCPeerConnectionFactory = RTCPeerConnectionFactory()
}
