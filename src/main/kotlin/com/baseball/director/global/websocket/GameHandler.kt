package com.baseball.director.global.websocket

import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.service.GamePlayService
import com.baseball.director.service.GameSetupService
import org.springframework.messaging.handler.annotation.*
import org.springframework.messaging.simp.SimpMessageSendingOperations
import org.springframework.stereotype.Controller

@Controller
class GameHandler(
    private val gamePlayService: GamePlayService,
    private val gameSetupService: GameSetupService,
    // matchInfoRepository는 Service가 데이터를 가져오므로 여기선 제거해도 됩니다!
    private val messagingTemplate: SimpMessageSendingOperations,
    private val matchInfoRepository: MatchInfoRepository
) {

    // [메인] 게임 진행 (타격, 작전, 교체 등)
    @MessageMapping("/match/{matchId}/command")
    @SendTo("/topic/match/{matchId}")
    fun handleGameAction(
        @DestinationVariable matchId: String,
        @Payload message: GameMessage
    ): GameResponse {

        println("📨 [${matchId}] 받은 메시지: type=${message.type}, sender=${message.senderId}")

        return try {
            // 1. Service 호출 (결과 메시지와 변경된 MatchInfo를 한 번에 받음)
            // 주의: GamePlayService가 GameActionResult를 반환하도록 수정되어 있어야 합니다.
            val actionResult = gamePlayService.handleAction(message)
            val resultMessage = actionResult.message
            val updatedMatchInfo = actionResult.matchInfo

            // 2. 이벤트 타입 결정
            val eventType = when {
                resultMessage.contains("공수교대") -> "GAME_EVENT"
                resultMessage.contains("경기 종료") -> "GAME_OVER"
                message.type == "MANAGEMENT" -> "GAME_EVENT" // 교체/작전 로그용
                else -> "AT_BAT_RESULT"
            }

            // 3. 응답 생성 (프론트 개발자의 Map 변환 로직 적용!)
            GameResponse(
                eventType = eventType,
                matchId = matchId,
                inning = updatedMatchInfo.inning,
                description = resultMessage,
                data = mapOf(
                    "success" to true,
                    // ⭐ 엔티티 대신 안전하게 변환된 Map을 보냅니다.
                    "matchInfo" to buildMatchInfoMap(updatedMatchInfo)
                ),
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

    // [설정] 게임 시작 전 설정 (라인업, 준비 완료)
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

                    // ⭐ [중요] 성능 이슈 방지: 전체 result를 보내지 않고 ready 값만 보냅니다.
                    val isReady = result["ready"] as? Boolean ?: false

                    GameResponse(
                        eventType = "READY_STATUS",
                        matchId = matchId,
                        inning = 0,
                        description = if (isReady) "게임 시작 준비 완료!" else "설정 진행 중...",
                        data = mapOf(
                            "ready" to isReady,
                            "home_team_id" to (result["home_team_id"] ?: 0L),
                            "away_team_id" to (result["away_team_id"] ?: 0L)
                        ),
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

    // [새로 추가] 게임 화면 진입 시 호출
    @MessageMapping("/match/{matchId}/enter")
    @SendTo("/topic/match/{matchId}")
    fun handleGameEnter(
        @DestinationVariable matchId: String,
        @Payload message: GameMessage
    ): GameResponse {

        println("🎮 [${matchId}] 게임 화면 진입: user=${message.senderId}")

        return try {
            val matchInfo = matchInfoRepository.findById(matchId).orElseThrow()

            // 게임 상태 확인
            val bothReady = matchInfo.homeLineupConfirmed && matchInfo.awayLineupConfirmed

            if (bothReady && matchInfo.status == "PLAYING") {
                // 게임 시작 메시지 전송
                GameResponse(
                    eventType = "GAME_START",
                    matchId = matchId,
                    inning = matchInfo.inning,
                    description = "⚾ 게임 시작! ${matchInfo.inning}회 ${if (matchInfo.isTop) "초" else "말"}",
                    data = mapOf(
                        "inning" to matchInfo.inning,
                        "is_top" to matchInfo.isTop,
                        "home_team_id" to (matchInfo.homeTeamId ?: 0L),  // ⭐ 0L로 기본값
                        "away_team_id" to (matchInfo.awayTeamId ?: 0L),  // ⭐ 0L로 기본값
                        "score" to mapOf(
                            "home" to matchInfo.score.home,
                            "away" to matchInfo.score.away
                        )
                    ),
                    timestamp = System.currentTimeMillis()
                )
            } else {
                GameResponse(
                    eventType = "WAITING",
                    matchId = matchId,
                    inning = 0,
                    description = "상대방 대기 중...",
                    data = emptyMap(),
                    timestamp = System.currentTimeMillis()
                )
            }

        } catch (e: Exception) {
            println("❌ [${matchId}] 진입 에러: ${e.message}")

            GameResponse(
                eventType = "ERROR",
                matchId = matchId,
                inning = 0,
                description = "오류: ${e.message}",
                data = emptyMap(),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    // ⭐ 프론트엔드 개발자분이 만든 Helper 함수 (Entity -> Map 변환)
    // 이 방식이 JSON 변환 오류도 막고 데이터도 깔끔해서 아주 좋습니다.
    private fun buildMatchInfoMap(matchInfo: com.baseball.director.domain.entity.MatchInfo?): Map<String, Any?> {
        if (matchInfo == null) return emptyMap()

        val isTop = matchInfo.isTop
        val defenseLineup = if (isTop) matchInfo.homeLineup else matchInfo.awayLineup

        return mapOf(
            "matchId" to matchInfo.matchId,
            "inning" to matchInfo.inning,
            "isTop" to matchInfo.isTop,
            "status" to matchInfo.status,
            "score" to mapOf(
                "home" to matchInfo.score.home,
                "away" to matchInfo.score.away
            ),
            "ballCount" to mapOf(
                "b" to matchInfo.ballCount.b,
                "s" to matchInfo.ballCount.s,
                "o" to matchInfo.ballCount.o
            ),
            "runnerIds" to matchInfo.runners.runnerIds,
            "currentBatterIndex" to matchInfo.currentBatterIndex,
            "currentPitcherId" to defenseLineup.starters["P"],
            "home_team_id" to matchInfo.homeTeamId,
            "away_team_id" to matchInfo.awayTeamId,
            // 프론트에서 수비 위치 렌더링에 필요한 정보가 있다면 여기에 추가 가능
            "fieldPositions" to mapOf<String, Long>() // 필요 시 구현
        )
    }
}