package com.baseball.director.controller

import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.RoomRepository
import com.baseball.director.service.TeamService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/simul")
class SimulationController(
    // ⭐ 여기가 핵심입니다! 이 부분(생성자)에 리포지토리들이 있어야 밑에서 쓸 수 있습니다.
    private val roomRepository: RoomRepository,
    private val matchInfoRepository: MatchInfoRepository,
    private val teamService: TeamService
) {

    @GetMapping("/{matchId}/init")
    fun loadGameData(@PathVariable matchId: String): Map<String, Any> {

        // 1. 방 정보 조회
        val room = roomRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("존재하지 않는 방입니다: $matchId") }

        // 2. 게스트 확인
        val guestId = room.guestId ?: throw IllegalStateException("게스트가 아직 입장하지 않았습니다.")

        // 3. 라인업 가져오기 (TeamService에 getLineup이 있어야 빨간줄이 안 뜹니다)
        val homeLineup = teamService.getLineup(matchId, room.hostId)
        val awayLineup = teamService.getLineup(matchId, guestId)

        // 4. 매치 정보 (구장 등)
        //val matchInfo = matchInfoRepository.findById(matchId).orElse(null)
        val stadiumId = 1L

        println("🎮 게임 데이터 로딩 완료: $matchId")

        return mapOf(
            "match_id" to matchId,
            "stadium" to mapOf("id" to stadiumId, "weather" to "CLEAR"),
            "home_team" to mapOf("user_id" to room.hostId, "role" to "HOME", "lineup" to homeLineup),
            "away_team" to mapOf("user_id" to guestId, "role" to "AWAY", "lineup" to awayLineup),
            "current_status" to room.status.name
        )
    }
}