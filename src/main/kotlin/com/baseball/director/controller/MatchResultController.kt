package com.baseball.director.controller

import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.RoomRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/match")
class MatchResultController(
    private val matchInfoRepository: MatchInfoRepository,
    private val roomRepository: RoomRepository
) {

    // [명세서 4번] 결과 요약 (스코어, 승패)
    @GetMapping("/{matchId}/summary")
    fun getMatchSummary(@PathVariable matchId: String): Map<String, Any> {
        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("매치 정보를 찾을 수 없습니다: $matchId") }

        val room = roomRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("방 정보를 찾을 수 없습니다: $matchId") }

        // ⭐ 수정됨: score 객체 안에서 점수 꺼내기
        val homeScore = matchInfo.score.home
        val awayScore = matchInfo.score.away

        // 승자 판별 로직
        val winner = when {
            homeScore > awayScore -> "HOME"
            awayScore > homeScore -> "AWAY"
            else -> "DRAW"
        }

        return mapOf(
            "match_id" to matchInfo.matchId,
            "home" to mapOf(
                "user_id" to room.hostId,
                "score" to homeScore
            ),
            "away" to mapOf(
                "user_id" to (room.guestId ?: 0L), // 게스트 없을 경우 대비
                "score" to awayScore
            ),
            "winner" to winner,
            "final_inning" to matchInfo.inning
        )
    }

    // [명세서 4번] 상세 박스스코어 (빈 껍데기)
    @GetMapping("/{matchId}/stats")
    fun getMatchStats(@PathVariable matchId: String): Map<String, Any> {
        return mapOf(
            "home_batter_stats" to listOf<Map<String, Any>>(),
            "away_batter_stats" to listOf<Map<String, Any>>(),
            "home_pitcher_stats" to listOf<Map<String, Any>>(),
            "away_pitcher_stats" to listOf<Map<String, Any>>()
        )
    }

    // [명세서 4번] 하이라이트 로그 (빈 껍데기)
    @GetMapping("/{matchId}/highlights")
    fun getHighlights(@PathVariable matchId: String): List<Map<String, Any>> {
        return emptyList()
    }
}