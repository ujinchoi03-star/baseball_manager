package com.baseball.director.controller

import com.baseball.director.domain.entity.Lineup
import com.baseball.director.domain.entity.Score
import com.baseball.director.domain.repository.MatchInfoRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simul")
class SimulationController(
    private val matchInfoRepository: MatchInfoRepository
) {

    @GetMapping("/{matchId}/init")
    fun loadGameData(@PathVariable matchId: String): ResponseEntity<GameInitResponse> {

        // 1. 경기 정보 가져오기
        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 경기입니다: $matchId") }

        // 2. 응답 데이터 구성
        val response = GameInitResponse(
            match_id = matchInfo.matchId, // 👈 [수정] id -> matchId 로 변경! (!!도 필요 없음)
            inning = matchInfo.inning,
            is_top = matchInfo.isTop,
            score = matchInfo.score,

            // 양 팀 라인업 전달
            home_lineup = matchInfo.homeLineup,
            away_lineup = matchInfo.awayLineup,

            // 볼카운트 & 주자 정보
            ball_count = matchInfo.ballCount,
            runners = matchInfo.runners.runnerIds
        )

        return ResponseEntity.ok(response)
    }
}

// 📦 응답용 DTO
data class GameInitResponse(
    val match_id: String,
    val inning: Int,
    val is_top: Boolean,
    val score: Score,
    val home_lineup: Lineup,
    val away_lineup: Lineup,
    val ball_count: com.baseball.director.domain.entity.BallCount,
    val runners: List<Long?>
)