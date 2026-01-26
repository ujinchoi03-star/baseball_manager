package com.baseball.director.global.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.*

@Configuration
@EnableWebSocketMessageBroker  // ⭐ 이거로 바꿈!
class WebSocketConfig : WebSocketMessageBrokerConfigurer {  // ⭐ 상속 변경!

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws-baseball")  // ⭐ 엔드포인트 변경
            .setAllowedOriginPatterns("*")
            .withSockJS()  // ⭐ SockJS 지원
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")  // ⭐ 브로드캐스트 경로
        registry.setApplicationDestinationPrefixes("/app")  // ⭐ 클라이언트→서버 경로
    }
}