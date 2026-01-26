package com.baseball.director.global.websocket

import com.baseball.director.service.GamePlayService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class GameHandler(
    private val gamePlayService: GamePlayService // ⭐ 서비스 연결!
) : TextWebSocketHandler() {

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val objectMapper = jacksonObjectMapper() // JSON 변환기

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        println("🔌 유저 접속: ${session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            // 1. JSON 메시지 해석 (String -> Object)
            val gameMessage = objectMapper.readValue(message.payload, GameMessage::class.java)

            println("📩 [게임요청] ${gameMessage.type} from ${gameMessage.senderId}")

            // 2. 서비스에게 일 시키기 (DB 업데이트 & 게임 로직)
            val resultText = gamePlayService.handleAction(gameMessage)

            // 3. 결과 전송 (일단 보낸 사람에게만)
            session.sendMessage(TextMessage(resultText))

            // (심화: 나중에는 같은 방에 있는 상대방 session을 찾아서 거기도 보내야 함)

        } catch (e: Exception) {
            println("🚨 에러 발생: ${e.message}")
            session.sendMessage(TextMessage("에러: ${e.message}"))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session.id)
    }
}