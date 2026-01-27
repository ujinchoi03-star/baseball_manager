package com.baseball.director.controller

import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.MatchRecordRepository
import com.baseball.director.domain.repository.RoomRepository
import com.baseball.director.domain.repository.BatterRepository
import com.baseball.director.domain.repository.PitcherRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/match")
class MatchResultController(
    private val matchInfoRepository: MatchInfoRepository,
    private val matchRecordRepository: MatchRecordRepository,
    private val roomRepository: RoomRepository,
    private val batterRepository: BatterRepository,
    private val pitcherRepository: PitcherRepository
) {

    private val objectMapper = jacksonObjectMapper()

    // [1] 결과 요약
    @GetMapping("/{matchId}/summary")
    fun getMatchSummary(@PathVariable matchId: String): Map<String, Any> {
        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("매치 정보를 찾을 수 없습니다: $matchId") }

        val room = roomRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("방 정보를 찾을 수 없습니다: $matchId") }

        val homeScore = matchInfo.score.home
        val awayScore = matchInfo.score.away

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
                "user_id" to (room.guestId ?: 0L),
                "score" to awayScore
            ),
            "winner" to winner,
            "final_inning" to matchInfo.inning
        )
    }

    // [2] 상세 박스스코어
    @GetMapping("/{matchId}/stats")
    fun getMatchStats(@PathVariable matchId: String): Map<String, Any> {
        try {
            val matchInfo = matchInfoRepository.findById(matchId)
                .orElseThrow { IllegalArgumentException("매치 정보를 찾을 수 없습니다") }

            // MATCH_RECORD에서 AT_BAT 이벤트 조회
            val records = matchRecordRepository.findByMatchIdAndEventType(matchId, "AT_BAT")

            // 타자 통계 집계
            val batterStatsMap = mutableMapOf<Long, MutableMap<String, Int>>()

            records.forEach { record ->
                try {
                    val data = objectMapper.readValue<Map<String, Any>>(record.data)
                    val batterId = (data["batter_id"] as? Number)?.toLong() ?: return@forEach
                    val result = data["result"] as? String ?: return@forEach

                    val stats = batterStatsMap.getOrPut(batterId) {
                        mutableMapOf("ab" to 0, "hit" to 0, "hr" to 0, "rbi" to 0, "run" to 0, "so" to 0)
                    }

                    if (result !in listOf("WALK", "HIT_BY_PITCH")) {
                        stats["ab"] = stats["ab"]!! + 1
                    }

                    when (result) {
                        "HIT" -> stats["hit"] = stats["hit"]!! + 1
                        "HOMERUN" -> {
                            stats["hit"] = stats["hit"]!! + 1
                            stats["hr"] = stats["hr"]!! + 1
                        }
                        "STRIKEOUT" -> stats["so"] = stats["so"]!! + 1
                    }

                    val scoreChange = (data["score_change"] as? Number)?.toInt() ?: 0
                    if (scoreChange > 0) {
                        stats["rbi"] = stats["rbi"]!! + scoreChange
                    }

                } catch (e: Exception) {
                    println("⚠️ Record 파싱 실패: ${e.message}")
                }
            }

            // 안전하게 타순 가져오기
            val homeBattingOrder = matchInfo.homeLineup.battingOrder
            val awayBattingOrder = matchInfo.awayLineup.battingOrder

            val homeBatterStats = homeBattingOrder.map { batterId ->
                val batter = batterRepository.findById(batterId).orElse(null)
                val stats = batterStatsMap[batterId] ?: mapOf("ab" to 0, "hit" to 0, "hr" to 0, "rbi" to 0, "run" to 0, "so" to 0)
                mapOf(
                    "player_id" to batterId,
                    "player_name" to (batter?.name ?: "Unknown"),
                    "position" to (batter?.position ?: "?"),
                    "ab" to stats["ab"],
                    "hit" to stats["hit"],
                    "hr" to stats["hr"],
                    "rbi" to stats["rbi"],
                    "run" to stats["run"],
                    "so" to stats["so"]
                )
            }

            val awayBatterStats = awayBattingOrder.map { batterId ->
                val batter = batterRepository.findById(batterId).orElse(null)
                val stats = batterStatsMap[batterId] ?: mapOf("ab" to 0, "hit" to 0, "hr" to 0, "rbi" to 0, "run" to 0, "so" to 0)
                mapOf(
                    "player_id" to batterId,
                    "player_name" to (batter?.name ?: "Unknown"),
                    "position" to (batter?.position ?: "?"),
                    "ab" to stats["ab"],
                    "hit" to stats["hit"],
                    "hr" to stats["hr"],
                    "rbi" to stats["rbi"],
                    "run" to stats["run"],
                    "so" to stats["so"]
                )
            }

            // ⭐ 투수 안전 처리 (!! 제거)
            val homePitcherId = matchInfo.homeLineup.starters["P"]
            val awayPitcherId = matchInfo.awayLineup.starters["P"]

            val homePitcherStats = if (homePitcherId != null) {
                val pitcher = pitcherRepository.findById(homePitcherId).orElse(null)
                listOf(mapOf(
                    "player_id" to homePitcherId,
                    "player_name" to (pitcher?.name ?: "Unknown"),
                    "ip" to matchInfo.inning,
                    "er" to matchInfo.score.away
                ))
            } else emptyList()

            val awayPitcherStats = if (awayPitcherId != null) {
                val pitcher = pitcherRepository.findById(awayPitcherId).orElse(null)
                listOf(mapOf(
                    "player_id" to awayPitcherId,
                    "player_name" to (pitcher?.name ?: "Unknown"),
                    "ip" to matchInfo.inning,
                    "er" to matchInfo.score.home
                ))
            } else emptyList()

            return mapOf(
                "home_batter_stats" to homeBatterStats,
                "away_batter_stats" to awayBatterStats,
                "home_pitcher_stats" to homePitcherStats,
                "away_pitcher_stats" to awayPitcherStats
            )

        } catch (e: Exception) {
            println("❌ Stats 에러: ${e.message}")
            e.printStackTrace()
            return mapOf(
                "home_batter_stats" to emptyList<Any>(),
                "away_batter_stats" to emptyList<Any>(),
                "home_pitcher_stats" to emptyList<Any>(),
                "away_pitcher_stats" to emptyList<Any>()
            )
        }
    }

    // [3] 하이라이트
    @GetMapping("/{matchId}/highlights")
    fun getHighlights(@PathVariable matchId: String): List<Map<String, Any>> {
        val records = matchRecordRepository.findByMatchIdAndEventType(matchId, "AT_BAT")

        return records.mapNotNull { record ->
            try {
                val data = objectMapper.readValue<Map<String, Any>>(record.data)
                val result = data["result"] as? String
                val hitType = (data["hit_type"] as? Number)?.toInt() ?: 1

                if ((result == "HOMERUN") || (result == "HIT" && hitType >= 2)) {
                    mapOf(
                        "inning" to record.inning,
                        "event" to result,
                        "description" to (record.description ?: ""),
                        "data" to data
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}