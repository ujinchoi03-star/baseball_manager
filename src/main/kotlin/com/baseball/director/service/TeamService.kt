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
    fun saveLineup(matchId: String, lineup: Lineup, userId: Long) {  // ⭐ userId 파라미터 추가
        val validationResult = validateLineup(lineup)
        if (!validationResult.isValid) {
            throw IllegalArgumentException(validationResult.message)
        }

        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseGet { MatchInfo(matchId = matchId) }

        // ⭐ userId로 home/away 판단
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

    // ⭐ 라인업 검증 로직
    private fun validateLineup(lineup: Lineup): ValidationResult {
        // 1. 수비 위치 9개 체크
        val requiredPositions = setOf("P", "C", "1B", "2B", "3B", "SS", "LF", "CF", "RF")
        val missingPositions = requiredPositions - lineup.starters.keys
        if (missingPositions.isNotEmpty()) {
            return ValidationResult(false, "빠진 수비 위치: $missingPositions")
        }

        // 2. 투수 확인
        val pitcher = lineup.starters["P"]
        if (pitcher == null) {
            return ValidationResult(false, "투수가 없습니다")
        }

        // 3. 타순 9명 체크
        if (lineup.battingOrder.size != 9) {
            return ValidationResult(false, "타순은 9명이어야 합니다 (현재: ${lineup.battingOrder.size}명)")
        }

        // 4. 수비 위치 중복 체크
        val uniqueFielders = lineup.starters.values.distinct()
        if (uniqueFielders.size != lineup.starters.size) {
            return ValidationResult(false, "수비 위치에 중복된 선수가 있습니다")
        }

        // 5. 타순 중복 체크
        val uniqueBatters = lineup.battingOrder.distinct()
        if (uniqueBatters.size != lineup.battingOrder.size) {
            return ValidationResult(false, "타순에 중복된 선수가 있습니다")
        }

        // 6. 투수가 타순에 있는지 확인 (일반적으로 투수는 타순 마지막)
        // KBO에서는 투수가 9번 타순인 경우가 많음
        if (!lineup.battingOrder.contains(pitcher)) {
            return ValidationResult(false, "투수가 타순에 없습니다")
        }

        return ValidationResult(true, "검증 성공")
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val message: String
)