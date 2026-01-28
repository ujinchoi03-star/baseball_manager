package com.baseball.director.global.websocket

import com.baseball.director.service.GamePlayService
import com.baseball.director.service.GameSetupService  // ⭐ 추가
import org.springframework.messaging.handler.annotation.*
import org.springframework.messaging.simp.SimpMessageSendingOperations
import org.springframework.stereotype.Controller

@Controller
class GameHandler(
    private val gamePlayService: GamePlayService,
    private val gameSetupService: GameSetupService,  // ⭐ 추가
    private val messagingTemplate: SimpMessageSendingOperations
) {

    // 기존 게임 액션 (타격 등)
    @MessageMapping("/match/{matchId}/command")
    @SendTo("/topic/match/{matchId}")
    fun handleGameAction(
        @DestinationVariable matchId: String,
        @Payload message: GameMessage
    ): GameResponse {

        println("📨 [${matchId}] 받은 메시지: type=${message.type}, sender=${message.senderId}")

        return try {
            val resultMessage = gamePlayService.handleAction(message)

            val eventType = when {
                resultMessage.contains("공수교대") -> "GAME_EVENT"
                resultMessage.contains("경기 종료") -> "GAME_EVENT"
                else -> "AT_BAT_RESULT"
            }

            GameResponse(
                eventType = eventType,
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
                data = mapOf("error" to (e.message ?: "알 수 없는 오류")),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // ⭐ 새로 추가: 게임 설정 메시지 (라인업 확정, 구장 선택 등)
    @MessageMapping("/match/{matchId}/setup")
    @SendTo("/topic/match/{matchId}")
    fun handleGameSetup(
        @DestinationVariable matchId: String,
        @Payload message: GameMessage
    ): GameResponse {

        println("🎮 [${matchId}] 설정 메시지: type=${message.type}, sender=${message.senderId}")

        return try {
            when (message.type) {
                "LINEUP_CONFIRM" -> {
                    val userId = message.senderId
                    val result = gameSetupService.confirmLineup(matchId, userId)

                    GameResponse(
                        eventType = "LINEUP_STATUS",
                        matchId = matchId,
                        inning = 0,
                        description = if (result["both_confirmed"] as Boolean) {
                            "양쪽 라인업 확정 완료!"
                        } else {
                            "상대방 라인업 대기 중..."
                        },
                        data = result,
                        timestamp = System.currentTimeMillis()
                    )
                }

                "CHECK_READY" -> {
                    val result = gameSetupService.checkReady(matchId)

                    GameResponse(
                        eventType = "READY_STATUS",
                        matchId = matchId,
                        inning = 0,
                        description = if (result["ready"] as Boolean) {
                            "게임 시작 준비 완료!"
                        } else {
                            "설정 진행 중..."
                        },
                        data = result,
                        timestamp = System.currentTimeMillis()
                    )
                }

                else -> {
                    throw IllegalArgumentException("알 수 없는 설정 타입: ${message.type}")
                }
            }

        } catch (e: Exception) {
            println("❌ [${matchId}] 설정 에러: ${e.message}")
            e.printStackTrace()

            GameResponse(
                eventType = "ERROR",
                matchId = matchId,
                inning = 0,
                description = "설정 오류: ${e.message}",
                data = mapOf("error" to (e.message ?: "알 수 없는 오류")),
                timestamp = System.currentTimeMillis()
            )
        }
    }
}