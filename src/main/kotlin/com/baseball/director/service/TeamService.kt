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

    // ⭐ 급여 한도 상수 설정
    companion object {
        const val MAX_CREDIT_LIMIT = 200
    }

    @Transactional(readOnly = true)
    fun getAllPlayers(): Map<String, Any> {
        return mapOf(
            "batters" to batterRepository.findAll(),
            "pitchers" to pitcherRepository.findAll()
        )
    }

    // ⭐ [NEW] 라인업에 포함된 모든 선수(선발+후보+투수+불펜)의 급여 합산 메서드
    @Transactional(readOnly = true)
    fun calculateLineupCredit(lineup: Lineup): Int {
        // 1. 투수 ID 수집 (선발 투수 'P' + 불펜 리스트)
        val pitcherIds = mutableListOf<Long>()
        lineup.starters["P"]?.let { pitcherIds.add(it) }
        pitcherIds.addAll(lineup.bullpen)

        // 2. 타자 ID 수집 (선발 포지션 중 투수 제외 + 벤치 리스트)
        val batterIds = lineup.starters.filterKeys { it != "P" }.values.toMutableList()
        batterIds.addAll(lineup.bench)

        // 3. DB 조회 (한 번에 조회하여 성능 최적화)
        val pitchers = pitcherRepository.findAllById(pitcherIds)
        val batters = batterRepository.findAllById(batterIds)

        // 4. 급여 합산
        val totalPitcherCredit = pitchers.sumOf { it.credit }
        val totalBatterCredit = batters.sumOf { it.credit }

        return totalPitcherCredit + totalBatterCredit
    }

    @Transactional
    fun saveLineup(matchId: String, lineup: Lineup, userId: Long) {
        // 1. 기존의 꼼꼼한 검증 로직 실행 (1~12번 항목)
        val validationResult = validateLineup(lineup)
        if (!validationResult.isValid) {
            throw IllegalArgumentException(validationResult.message)
        }

        // 2. ⭐ [추가됨] 급여(Credit) 총합 검증
        val totalCredit = calculateLineupCredit(lineup)
        println("💰 라인업 총 급여: $totalCredit / $MAX_CREDIT_LIMIT") // 로그 확인용

        if (totalCredit > MAX_CREDIT_LIMIT) {
            throw IllegalArgumentException("총 급여가 초과되었습니다! (현재: $totalCredit / 한도: $MAX_CREDIT_LIMIT)")
        }

        // 3. 매치 정보 저장 로직
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

    // 기존 검증 로직 (100% 유지)
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

        // 10. 벤치 멤버 수 확인 (5명)
        if (lineup.bench.size != 5) {
            return ValidationResult(false, "벤치 멤버는 정확히 5명이어야 합니다. (현재: ${lineup.bench.size}명)")
        }

        // 11. 불펜 투수 수 확인 (6명)
        if (lineup.bullpen.size != 6) {
            return ValidationResult(false, "불펜 투수(마무리 포함)는 정확히 6명이어야 합니다. (현재: ${lineup.bullpen.size}명)")
        }

        // 12. 벤치/불펜 중복 체크 (선발이랑 겹치는지, 자기들끼리 겹치는지)
        val allStarters = lineup.starters.values.toSet()
        val allBench = lineup.bench.toSet()
        val allBullpen = lineup.bullpen.toSet()

        // 벤치에 중복 선수가 있거나, 선발과 겹치는지
        if (allBench.size != 5 || allBench.any { it in allStarters }) {
            return ValidationResult(false, "벤치에 중복된 선수가 있거나 선발 선수와 겹칩니다.")
        }

        // 불펜에 중복 선수가 있거나, 선발과 겹치는지
        if (allBullpen.size != 6 || allBullpen.any { it in allStarters }) {
            return ValidationResult(false, "불펜에 중복된 선수가 있거나 선발 선수와 겹칩니다.")
        }

        return ValidationResult(true, "검증 성공")
    }

    @Transactional(readOnly = true)
    fun getLineup(matchId: String, userId: Long): Lineup {
        val room = roomRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("방을 찾을 수 없습니다") }

        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseThrow { IllegalArgumentException("매치 정보를 찾을 수 없습니다") }

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