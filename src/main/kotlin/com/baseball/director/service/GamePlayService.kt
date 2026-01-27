package com.baseball.director.service

import com.baseball.director.domain.entity.MatchInfo
import com.baseball.director.domain.game.InningState
import com.baseball.director.domain.repository.BatterRepository
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.PitcherRepository
import com.baseball.director.global.websocket.GameMessage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GamePlayService(
    private val matchInfoRepository: MatchInfoRepository,
    private val batterRepository: BatterRepository,
    private val pitcherRepository: PitcherRepository,
    private val gameEngineService: GameEngineService,
    private val baseRunningService: BaseRunningService
) {

    @Transactional
    fun handleAction(message: GameMessage): String {

        // 1. 경기 정보 가져오기
        val matchInfo = matchInfoRepository.findById(message.matchId)
            .orElseThrow { IllegalArgumentException("경기를 찾을 수 없습니다: ${message.matchId}") }

        // 2. 공격/수비 라인업 결정
        val isTop = matchInfo.isTop
        val attackLineup = if (isTop) matchInfo.awayLineup else matchInfo.homeLineup
        val defenseLineup = if (isTop) matchInfo.homeLineup else matchInfo.awayLineup

        // 3. 현재 타자 & 투수 찾기
        // ⭐ [수정] MatchInfo의 단일 인덱스 대신, 팀별 Lineup에 저장된 순서를 사용
        val currentBatterIdx = attackLineup.currentOrder
        val currentBatterId = attackLineup.battingOrder.getOrNull(currentBatterIdx)
            ?: throw IllegalStateException("타순 정보가 잘못되었습니다. (Index: $currentBatterIdx)")

        val currentPitcherId = defenseLineup.starters["P"]
            ?: throw IllegalStateException("상대 팀 투수가 없습니다.")

        val batter = batterRepository.findById(currentBatterId).orElseThrow()
        val pitcher = pitcherRepository.findById(currentPitcherId).orElseThrow()

        // 4. 수비수 정보 로딩 (시뮬레이션용)
        val defenseIds = defenseLineup.starters.values.filter { it != currentPitcherId }.toList()
        val defensePlayers = batterRepository.findAllById(defenseIds)

        // 5. 이닝 상태(주자, 아웃) 구성
        // ⭐ [수정] JSON 객체 구조에 맞춰 접근 (runners.runnerIds)
        val state = InningState(
            outCount = matchInfo.ballCount.o,
            currentScore = if (isTop) matchInfo.score.away else matchInfo.score.home
        ).apply {
            firstBase = matchInfo.runners.runnerIds[0]?.let { batterRepository.findById(it).orElse(null) }
            secondBase = matchInfo.runners.runnerIds[1]?.let { batterRepository.findById(it).orElse(null) }
            thirdBase = matchInfo.runners.runnerIds[2]?.let { batterRepository.findById(it).orElse(null) }
        }

        // 6. 게임 엔진 돌리기 (결과 예측)
        val estimatedPitchCount = matchInfo.inning * 15 // 투구수 대략 계산
        val playResult = gameEngineService.playBall(
            pitcher = pitcher,
            batter = batter,
            defensePlayers = defensePlayers,
            isRunnerOnFirst = state.firstBase != null,
            outCount = state.outCount,
            currentPitchCount = estimatedPitchCount
        )

        // 7. 주루 플레이 처리 (점수, 아웃, 주자 이동)
        baseRunningService.processPlay(state, playResult, batter)

        // 8. 결과 반영 (MatchInfo 업데이트)
        matchInfo.ballCount.o = state.outCount
        matchInfo.runners.runnerIds[0] = state.firstBase?.id
        matchInfo.runners.runnerIds[1] = state.secondBase?.id
        matchInfo.runners.runnerIds[2] = state.thirdBase?.id

        if (isTop) matchInfo.score.away = state.currentScore
        else matchInfo.score.home = state.currentScore

        // 로그 메시지 생성
        var resultMessage = "⚾ ${batter.name}: ${playResult.detail}"
        if (state.scoreLog.isNotEmpty()) {
            resultMessage += "\n👏 " + state.scoreLog.joinToString(", ")
        }

        // 9. 타순 변경 (다음 타자로)
        // ⭐ [핵심] 아웃이든 안타든 타석이 끝났으면 다음 타자로 넘김
        attackLineup.currentOrder = (currentBatterIdx + 1) % 9

        // 프론트엔드 표시용 통합 인덱스 업데이트 (현재 치는 타자)
        matchInfo.currentBatterIndex = attackLineup.currentOrder

        // 10. 3아웃 공수교대 체크
        if (matchInfo.ballCount.o >= 3) {
            changeInning(matchInfo)
            resultMessage += "\n🔄 3아웃 공수교대! (${matchInfo.inning}회 ${if (matchInfo.isTop) "초" else "말"})"

            // 공수교대 후, 공격팀이 바뀌었으므로 'currentBatterIndex'를 새 공격팀의 순서로 맞춰줌
            val newLineup = if (matchInfo.isTop) matchInfo.awayLineup else matchInfo.homeLineup
            matchInfo.currentBatterIndex = newLineup.currentOrder
        }

        // JPA Dirty Checking으로 자동 저장되지만, JSON 필드 변경 확실히 하기 위해 명시적 저장
        matchInfoRepository.save(matchInfo)

        return resultMessage
    }

    private fun changeInning(matchInfo: MatchInfo) {
        // 아웃, 볼카운트, 주자 초기화
        matchInfo.ballCount.o = 0
        matchInfo.ballCount.b = 0
        matchInfo.ballCount.s = 0
        matchInfo.runners.runnerIds.replaceAll { null } // 리스트 내부 null로 초기화

        // 공수 전환
        matchInfo.isTop = !matchInfo.isTop

        // 말이 끝나고 초로 갈 때 이닝 증가 (또는 초->말 규칙에 따라 수정)
        // 보통: 1회초 -> 1회말 -> 2회초
        if (matchInfo.isTop) {
            matchInfo.inning++
        }
    }
}