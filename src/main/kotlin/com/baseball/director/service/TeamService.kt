package com.baseball.director.service

import com.baseball.director.domain.entity.Lineup
import com.baseball.director.domain.entity.MatchInfo
import com.baseball.director.domain.repository.BatterRepository
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.PitcherRepository
import com.baseball.director.domain.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val batterRepository: BatterRepository,
    private val pitcherRepository: PitcherRepository,
    private val matchInfoRepository: MatchInfoRepository,
    private val roomRepository: RoomRepository
) {

    @Transactional(readOnly = true)
    fun getAllPlayers(): Map<String, Any> {
        return mapOf(
            "batters" to batterRepository.findAll(),
            "pitchers" to pitcherRepository.findAll()
        )
    }

    @Transactional
    fun saveLineup(matchId: String, lineup: Lineup, userId: Long) {
        val validationResult = validateLineup(lineup)
        if (!validationResult.isValid) {
            throw IllegalArgumentException(validationResult.message)
        }

        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseGet { MatchInfo(matchId = matchId) }

        val room = roomRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("매칭 정보를 찾을 수 없습니다") }

        if (room.hostId == userId) {
            matchInfo.homeLineup = lineup
            println("✅ Home 라인업 저장 완료 (userId: $userId)")
        } else {
            matchInfo.awayLineup = lineup
            println("✅ Away 라인업 저장 완료 (userId: $userId)")
        }

        matchInfoRepository.save(matchInfo)
    }

    private fun validateLineup(lineup: Lineup): ValidationResult {
        // 1. 수비 위치 10개 체크 (야수 8명 + DH 1명 + 투수 1명)
        val requiredPositions = setOf("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF", "DH")
        val missingPositions = requiredPositions - lineup.starters.keys
        if (missingPositions.isNotEmpty()) {
            return ValidationResult(false, "빠진 수비 위치: $missingPositions")
        }

        // 2. 투수 확인
        val pitcher = lineup.starters["P"]
        if (pitcher == null) {
            return ValidationResult(false, "투수가 없습니다")
        }

        // 3. 지명타자 확인
        val dh = lineup.starters["DH"]
        if (dh == null) {
            return ValidationResult(false, "지명타자(DH)가 없습니다")
        }

        // 4. 타순 9명 체크 (투수는 타순에 없음, DH가 대신 타석에 섬)
        if (lineup.battingOrder.size != 9) {
            return ValidationResult(false, "타순은 9명이어야 합니다 (현재: ${lineup.battingOrder.size}명)")
        }

        // 5. 투수는 타순에 없어야 함
        if (lineup.battingOrder.contains(pitcher)) {
            return ValidationResult(false, "투수는 타순에 포함되면 안 됩니다 (DH가 대신 타석)")
        }

        // 6. DH는 타순에 있어야 함
        if (!lineup.battingOrder.contains(dh)) {
            return ValidationResult(false, "지명타자(DH)는 타순에 포함되어야 합니다")
        }

        // 7. 수비 위치 중복 체크 (투수와 DH 제외한 나머지)
        val fielders = lineup.starters.filterKeys { it != "P" && it != "DH" }.values
        val uniqueFielders = fielders.distinct()
        if (uniqueFielders.size != fielders.size) {
            return ValidationResult(false, "수비 위치에 중복된 선수가 있습니다")
        }

        // 8. 타순 중복 체크
        val uniqueBatters = lineup.battingOrder.distinct()
        if (uniqueBatters.size != lineup.battingOrder.size) {
            return ValidationResult(false, "타순에 중복된 선수가 있습니다")
        }

        // 9. 타순의 모든 선수가 수비 위치에 있는지 확인 (DH 포함)
        val allPlayers = lineup.starters.values.toSet()
        val invalidBatters = lineup.battingOrder.filterNot { it in allPlayers }
        if (invalidBatters.isNotEmpty()) {
            return ValidationResult(false, "타순에 수비 위치가 없는 선수가 있습니다: $invalidBatters")
        }

        return ValidationResult(true, "검증 성공")
    }
    @Transactional(readOnly = true)
    fun getLineup(matchId: String, userId: Long): Lineup {
        val room = roomRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("방을 찾을 수 없습니다") }

        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("매치 정보를 찾을 수 없습니다") }

        // ⭐ 수정됨: MatchInfo에서 Lineup이 null이 아니므로 바로 반환
        return if (userId == room.hostId) {
            matchInfo.homeLineup
        } else {
            matchInfo.awayLineup
        }
    }
}



data class ValidationResult(
    val isValid: Boolean,
    val message: String
)