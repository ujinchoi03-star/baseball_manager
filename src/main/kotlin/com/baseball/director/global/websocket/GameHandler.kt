package com.baseball.director.global.websocket

import com.baseball.director.service.GamePlayService
import org.springframework.messaging.handler.annotation.*
import org.springframework.messaging.simp.SimpMessageSendingOperations
import org.springframework.stereotype.Controller

@Controller
class GameHandler(
    private val gamePlayService: GamePlayService,
    private val messagingTemplate: SimpMessageSendingOperations
) {

    @MessageMapping("/match/{matchId}/command")
    @SendTo("/topic/match/{matchId}")
    fun handleGameAction(
        @DestinationVariable matchId: String,
        @Payload message: GameMessage
    ): GameResponse {

        println("📨 [${matchId}] 받은 메시지: type=${message.type}, sender=${message.senderId}")

        return try {
            val resultMessage = gamePlayService.handleAction(message)

            GameResponse(
                eventType = "AT_BAT_RESULT",
                matchId = matchId,
                inning = message.inning ?: 1,
                description = resultMessage,
                data = mapOf("success" to true),
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            println("❌ [${matchId}] 에러 발생: ${e.message}")
            e.printStackTrace()

            GameResponse(
                eventType = "ERROR",
                matchId = matchId,
                inning = 1,
                description = "오류가 발생했습니다: ${e.message ?: "알 수 없는 오류"}",
                data = mapOf("error" to (e.message ?: "알 수 없는 오류")),  // ⭐ 수정
                timestamp = System.currentTimeMillis()
            )
        }
    }

    fun broadcastToMatch(matchId: String, response: GameResponse) {
        messagingTemplate.convertAndSend("/topic/match/$matchId", response)
    }
}