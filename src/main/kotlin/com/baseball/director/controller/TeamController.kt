package com.baseball.director.controller

import com.baseball.director.domain.entity.Lineup
import com.baseball.director.service.TeamService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/team")
class TeamController(
    private val teamService: TeamService
) {

    @GetMapping("/players")
    fun getAllPlayers(): Map<String, Any> {
        return teamService.getAllPlayers()
    }

    @PostMapping("/lineup")
    fun saveLineup(@RequestBody request: LineupRequest): Map<String, String> {
        // ⭐ 서비스 호출!
        teamService.saveLineup(request.matchId, request.activeLineup)

        println("📝 DB 저장 완료! MatchID: ${request.matchId}")
        return mapOf("status" to "SUCCESS", "message" to "라인업이 DB에 저장되었습니다.")
    }
}

// DTO 수정: Map -> Lineup 클래스 직접 사용 (자동 변환됨)
data class LineupRequest(
    val matchId: String,
    val activeLineup: Lineup
)