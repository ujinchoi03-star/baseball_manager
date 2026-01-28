package com.baseball.director.service

import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GameSetupService(
    private val matchInfoRepository: MatchInfoRepository,
    private val roomRepository: RoomRepository
) {

    // 라인업 확정
    @Transactional
    fun confirmLineup(matchId: String, userId: Long): Map<String, Any> {
        val matchInfo = matchInfoRepository.findById(matchId).orElse(null)
            ?: throw IllegalArgumentException("매치를 찾을 수 없습니다")

        val room = roomRepository.findById(matchId).orElse(null)
            ?: throw IllegalArgumentException("방을 찾을 수 없습니다")

        // 홈팀인지 원정팀인지 판단
        val isHome = (userId == room.hostId)

        if (isHome) {
            matchInfo.homeLineupConfirmed = true
        } else {
            matchInfo.awayLineupConfirmed = true
        }

        matchInfoRepository.save(matchInfo)

        val bothConfirmed = matchInfo.homeLineupConfirmed && matchInfo.awayLineupConfirmed

        println("✅ 라인업 확정: matchId=$matchId, userId=$userId, isHome=$isHome, both=$bothConfirmed")

        return mapOf(
            "home_confirmed" to matchInfo.homeLineupConfirmed,
            "away_confirmed" to matchInfo.awayLineupConfirmed,
            "both_confirmed" to bothConfirmed,
            "home_team_id" to (matchInfo.homeTeamId ?: 0L),  // ⭐ 추가
            "away_team_id" to (matchInfo.awayTeamId ?: 0L)   // ⭐ 추가
        )
    }

    // 게임 시작 준비 확인
    @Transactional(readOnly = true)
    fun checkReady(matchId: String): Map<String, Any> {
        val matchInfo = matchInfoRepository.findById(matchId).orElse(null)
            ?: throw IllegalArgumentException("매치를 찾을 수 없습니다")

        // 라인업 확정만 체크 (homeTeamId, awayTeamId는 이미 자동 설정됨)
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