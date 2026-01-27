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

    @PostMapping("/match_setup")
    fun confirmMatchSetup(@RequestBody request: MatchSetupRequest): Map<String, Any> {

        // TODO: 나중에 TeamService에 createMatchSetup(request) 같은 함수를 만들어서 DB에 저장해야 함.
        // 지금은 API 연결 확인을 위해 더미 응답만 반환합니다.
        println("🏟️ 경기 설정 확정: Match(${request.match_id}), Stadium(${request.stadium_id}), Home(${request.is_home})")

        return mapOf(
            "status" to "READY",
            "match_id" to request.match_id
        )
    }
}

    @GetMapping("/lineup_check")
    fun checkLineup(): Map<String, Any> {
        return mapOf("status" to "OK", "total_credit" to 0)
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

data class MatchSetupRequest(
    val match_id: String,
    val user_id: Long,
    val stadium_id: Long,  // 구장 ID
    val is_home: Boolean   // true면 홈팀(후공), false면 원정팀(선공) 등 규칙에 따름
)