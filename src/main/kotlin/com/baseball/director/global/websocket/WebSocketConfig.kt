package com.baseball.director.global.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val gameHandler: GameHandler
) : WebSocketConfigurer {

    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        // "ws://localhost:8080/ws/game" 주소로 들어오면 gameHandler가 담당한다!
        registry.addHandler(gameHandler, "/ws/game")
            .setAllowedOrigins("*") // 모든 곳에서 접속 허용 (CORS 해결)
    }
}