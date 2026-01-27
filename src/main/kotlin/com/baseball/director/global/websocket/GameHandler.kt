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

    // 클라이언트가 보내는 곳: /app/match/{matchId}/command
    @MessageMapping("/match/{matchId}/command")
    @SendTo("/topic/match/{matchId}") // 구독하는 곳
    fun handleGameAction(
        @DestinationVariable matchId: String,
        @Payload message: GameMessage
    ): GameResponse {

        println("📨 [${matchId}] 받은 메시지: type=${message.type}, sender=${message.senderId}")

        return try {
            // 1. 게임 로직 실행
            val resultMessage = gamePlayService.handleAction(message)

            // 2. ⭐ [핵심 수정] 메시지 내용에 따라 이벤트 타입 자동 결정
            // 명세서의 GAME_EVENT(이닝 교체 등)를 지원하기 위함
            val eventType = when {
                resultMessage.contains("공수교대") -> "GAME_EVENT"
                resultMessage.contains("경기 종료") -> "GAME_EVENT"
                else -> "AT_BAT_RESULT" // 일반적인 안타/아웃
            }

            // 3. 응답 생성
            GameResponse(
                eventType = eventType,  // ⭐ 동적으로 바뀐 타입 넣어주기
                matchId = matchId,
                inning = message.inning ?: 1, // 필요하다면 서비스에서 현재 이닝을 리턴받도록 개선 가능
                description = resultMessage,
                data = mapOf("success" to true),
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            println("❌ [${matchId}] 에러 발생: ${e.message}")
            e.printStackTrace()

            // 에러 발생 시 명세서대로 ERROR 타입 전송
            GameResponse(
                eventType = "ERROR",
                matchId = matchId,
                inning = 1,
                description = "오류가 발생했습니다: ${e.message ?: "알 수 없는 오류"}",
                data = mapOf("error" to (e.message ?: "알 수 없는 오류")),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // 서버에서 강제로 메시지를 보낼 때 쓰는 함수 (필요 시 사용)
 //   fun broadcastToMatch(matchId: String, response: GameResponse) {
   //     messagingTemplate.convertAndSend("/topic/match/$matchId", response)
    //}
}