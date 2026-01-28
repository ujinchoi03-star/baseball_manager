package com.baseball.director.controller

import com.baseball.director.domain.entity.Lineup
import com.baseball.director.service.TeamService
import org.springframework.http.ResponseEntity
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
    fun saveLineup(@RequestBody request: SaveLineupRequest): ResponseEntity<Map<String, String>> {
        // DTO -> Entity 변환
        val lineup = Lineup(
            battingOrder = request.active_lineup.batting_order.toMutableList(),
            starters = request.active_lineup.starters.toMutableMap(),
            bench = request.active_lineup.bench?.toMutableList() ?: mutableListOf(),
            bullpen = request.active_lineup.bullpen?.toMutableList() ?: mutableListOf(),
            hasDH = request.active_lineup.has_dh ?: true
        )

        return try {
            // 서비스 호출 (급여 검증 포함)
            teamService.saveLineup(request.match_id, lineup, request.user_id)
            ResponseEntity.ok(mapOf("status" to "SUCCESS", "match_id" to request.match_id))
        } catch (e: IllegalArgumentException) {
            // 검증 실패 시 400 Bad Request 리턴
            ResponseEntity.badRequest().body(mapOf("status" to "FAIL", "message" to (e.message ?: "오류 발생")))
        }
    }

    // ⭐ [수정] 현재 라인업의 급여 합계 확인 API
    @PostMapping("/lineup_check")
    fun checkLineup(@RequestBody request: SaveLineupRequest): Map<String, Any> {
        val lineup = Lineup(
            battingOrder = request.active_lineup.batting_order.toMutableList(),
            starters = request.active_lineup.starters.toMutableMap(),
            bench = request.active_lineup.bench?.toMutableList() ?: mutableListOf(),
            bullpen = request.active_lineup.bullpen?.toMutableList() ?: mutableListOf(),
            hasDH = request.active_lineup.has_dh ?: true
        )

        // 현재 구성된 라인업의 총 급여 계산
        val totalCredit = teamService.calculateLineupCredit(lineup)

        return mapOf(
            "status" to "OK",
            "total_credit" to totalCredit,
            "limit" to 200,
            "is_valid" to (totalCredit <= 200)
        )
    }

    @PostMapping("/match_setup")
    fun confirmMatchSetup(@RequestBody request: MatchSetupRequest): Map<String, Any> {
        println("🏟️ 경기 설정 확정: Match(${request.match_id}), Stadium(${request.stadium_id})")
        return mapOf("status" to "READY", "match_id" to request.match_id)
    }
}

// --- DTO ---
data class SaveLineupRequest(
    val match_id: String,
    val user_id: Long,
    val active_lineup: ActiveLineup
)

data class ActiveLineup(
    val starters: Map<String, Long>,
    val batting_order: List<Long>,
    val bench: List<Long>? = null,
    val bullpen: List<Long>? = null,
    val has_dh: Boolean? = true
)

data class MatchSetupRequest(
    val match_id: String,
    val user_id: Long,
    val stadium_id: Long,
    val is_home: Boolean
)