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
    fun saveLineup(@RequestBody request: SaveLineupRequest): Map<String, String> {
        val lineup = Lineup(
            battingOrder = request.active_lineup.batting_order.toMutableList(),
            starters = request.active_lineup.starters.toMutableMap()
        )

        teamService.saveLineup(request.match_id, lineup, request.user_id)  // ⭐ user_id 추가

        return mapOf("status" to "SUCCESS", "match_id" to request.match_id)
    }

    @GetMapping("/lineup_check")
    fun checkLineup(): Map<String, Any> {
        return mapOf("status" to "OK", "total_credit" to 0)
    }
}

data class SaveLineupRequest(
    val match_id: String,
    val user_id: Long,
    val active_lineup: ActiveLineup
)

data class ActiveLineup(
    val starters: Map<String, Long>,
    val batting_order: List<Long>,
    val bench: List<Long>? = null,
    val bullpen: List<Long>? = null
)