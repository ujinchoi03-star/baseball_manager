package com.baseball.director.service

import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.RoomRepository
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameSetupService(
    private val matchInfoRepository: MatchInfoRepository,
    private val roomRepository: RoomRepository,
    private val messagingTemplate: SimpMessagingTemplate  // ⭐ 추가
) {

    // 라인업 확정
    @Transactional
    fun confirmLineup(matchId: String, userId: Long): Map<String, Any> {
        val matchInfo = matchInfoRepository.findById(matchId).orElse(null)
            ?: throw IllegalArgumentException("매치를 찾을 수 없습니다")

        val room = roomRepository.findById(matchId).orElse(null)
            ?: throw IllegalArgumentException("방을 찾을 수 없습니다")

        val isHome = (userId == room.hostId)

        if (isHome) {
            matchInfo.homeLineupConfirmed = true
        } else {
            matchInfo.awayLineupConfirmed = true
        }

        matchInfoRepository.save(matchInfo)

        val bothConfirmed = matchInfo.homeLineupConfirmed && matchInfo.awayLineupConfirmed

        println("✅ 라인업 확정: matchId=$matchId, userId=$userId, isHome=$isHome, both=$bothConfirmed")

        // ⭐ 양쪽 모두 확정되면 게임 시작!
        if (bothConfirmed) {
            matchInfo.status = "PLAYING"
            matchInfoRepository.save(matchInfo)

            // 게임 시작 메시지 전송
            try {
                messagingTemplate.convertAndSend(
                    "/topic/match/$matchId",
                    mapOf(
                        "eventType" to "GAME_START",
                        "matchId" to matchId,
                        "inning" to 1,
                        "description" to "⚾ 게임 시작! 1회 초",
                        "data" to mapOf(
                            "inning" to 1,
                            "is_top" to true,
                            "home_team_id" to matchInfo.homeTeamId,
                            "away_team_id" to matchInfo.awayTeamId,
                            "score" to mapOf(
                                "home" to 0,
                                "away" to 0
                            )
                        ),
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                println("🎮 게임 시작 메시지 전송 완료!")
            } catch (e: Exception) {
                println("⚠️ 게임 시작 메시지 전송 실패: ${e.message}")
            }
        }

        return mapOf(
            "home_confirmed" to matchInfo.homeLineupConfirmed,
            "away_confirmed" to matchInfo.awayLineupConfirmed,
            "both_confirmed" to bothConfirmed,
            "home_team_id" to (matchInfo.homeTeamId ?: 0L),
            "away_team_id" to (matchInfo.awayTeamId ?: 0L)
        )
    }

    // 게임 시작 준비 확인
    @Transactional(readOnly = true)
    fun checkReady(matchId: String): Map<String, Any> {
        val matchInfo = matchInfoRepository.findById(matchId).orElse(null)
            ?: throw IllegalArgumentException("매치를 찾을 수 없습니다")

        val ready = matchInfo.homeLineupConfirmed &&
                matchInfo.awayLineupConfirmed

        return mapOf(
            "ready" to ready,
            "home_confirmed" to matchInfo.homeLineupConfirmed,
            "away_confirmed" to matchInfo.awayLineupConfirmed,
            "home_team_id" to (matchInfo.homeTeamId ?: 0L),
            "away_team_id" to (matchInfo.awayTeamId ?: 0L)
        )
    }
}