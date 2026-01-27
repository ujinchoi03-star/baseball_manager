package com.baseball.director.controller

import com.baseball.director.domain.entity.Lineup
import com.baseball.director.domain.entity.Score
import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.entity.Pitcher
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.BatterRepository
import com.baseball.director.domain.repository.PitcherRepository
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/simul")
class SimulationController(
    private val matchInfoRepository: MatchInfoRepository,
    private val batterRepository: BatterRepository,
    private val pitcherRepository: PitcherRepository
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
            home_lineup = convertToFullLineup(matchInfo.homeLineup),
            away_lineup = convertToFullLineup(matchInfo.awayLineup),
            ball_count = matchInfo.ballCount,
            runners = matchInfo.runners.runnerIds.map { id ->
                id?.let { batterRepository.findById(it).orElse(null) }
            }
        )

        return ResponseEntity.ok(response)
    }

    private fun convertToFullLineup(lineup: Lineup): FullLineupResponse {
        val starters = lineup.starters.mapValues { (pos, id) ->
            if (pos == "P") {
                pitcherRepository.findById(id).orElse(null)
            } else {
                batterRepository.findById(id).orElse(null)
            }
        }

        val battingOrder = lineup.battingOrder.map { id ->
            batterRepository.findById(id).orElse(null)
        }

        val bench = lineup.bench.map { id ->
            batterRepository.findById(id).orElse(null)
        }

        val bullpen = lineup.bullpen.map { id ->
            pitcherRepository.findById(id).orElse(null)
        }

        return FullLineupResponse(
            starters = starters,
            batting_order = battingOrder,
            bench = bench,
            bullpen = bullpen,
            hasDH = lineup.hasDH
        )
    }
}

// 📦 응답용 DTO
data class GameInitResponse(
    val match_id: String,
    val inning: Int,
    val is_top: Boolean,
    val score: Score,
    val home_lineup: FullLineupResponse,
    val away_lineup: FullLineupResponse,
    val ball_count: com.baseball.director.domain.entity.BallCount,
    val runners: List<Batter?>
)

data class FullLineupResponse(
    val starters: Map<String, Any?>,
    val batting_order: List<Batter?>,
    val bench: List<Batter?>,
    val bullpen: List<Pitcher?>,
    val hasDH: Boolean
)