package com.baseball.director.service

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

        val matchInfo = matchInfoRepository.findById(message.matchId)
            .orElseThrow { IllegalArgumentException("경기를 찾을 수 없습니다: ${message.matchId}") }

        val isTop = matchInfo.isTop
        val attackLineup = if (isTop) matchInfo.awayLineup else matchInfo.homeLineup
        val defenseLineup = if (isTop) matchInfo.homeLineup else matchInfo.awayLineup

        val currentBatterIdx = matchInfo.currentBatterIndex ?: 0
        val currentBatterId = attackLineup.battingOrder.getOrNull(currentBatterIdx)
            ?: throw IllegalStateException("타순 정보가 없습니다")

        val currentPitcherId = defenseLineup.starters["P"]
            ?: throw IllegalStateException("투수 정보가 없습니다")

        val batter = batterRepository.findById(currentBatterId).orElseThrow()
        val pitcher = pitcherRepository.findById(currentPitcherId).orElseThrow()

        val defenseIds = defenseLineup.starters.values.toList()
        val defensePlayers = batterRepository.findAllById(defenseIds)

        val state = InningState(
            outCount = matchInfo.ballCount.o,
            currentScore = if (isTop) matchInfo.score.away else matchInfo.score.home
        ).apply {
            firstBase = matchInfo.runners.runnerIds[0]?.let { batterRepository.findById(it).orElse(null) }
            secondBase = matchInfo.runners.runnerIds[1]?.let { batterRepository.findById(it).orElse(null) }
            thirdBase = matchInfo.runners.runnerIds[2]?.let { batterRepository.findById(it).orElse(null) }
        }

        val estimatedPitchCount = matchInfo.inning * 15
        val playResult = gameEngineService.playBall(
            pitcher = pitcher,
            batter = batter,
            defensePlayers = defensePlayers,
            isRunnerOnFirst = state.firstBase != null,
            outCount = state.outCount,
            currentPitchCount = estimatedPitchCount
        )

        baseRunningService.processPlay(state, playResult, batter)

        matchInfo.ballCount.o = state.outCount
        matchInfo.runners.runnerIds[0] = state.firstBase?.id
        matchInfo.runners.runnerIds[1] = state.secondBase?.id
        matchInfo.runners.runnerIds[2] = state.thirdBase?.id

        if (isTop) matchInfo.score.away = state.currentScore
        else matchInfo.score.home = state.currentScore

        var resultMessage = "⚾ ${batter.name}: ${playResult.detail}"
        if (state.scoreLog.isNotEmpty()) {
            resultMessage += "\n👏 " + state.scoreLog.joinToString(", ")
        }

        if (matchInfo.ballCount.o >= 3) {
            changeInning(matchInfo)
            resultMessage += "\n🔄 3아웃 공수교대! (${matchInfo.inning}회 ${if (matchInfo.isTop) "초" else "말"})"
        }

        // 타자 인덱스 업데이트
        matchInfo.currentBatterIndex = (currentBatterIdx + 1) % 9

        matchInfoRepository.save(matchInfo)

        return resultMessage
    }

    private fun changeInning(matchInfo: com.baseball.director.domain.entity.MatchInfo) {
        matchInfo.ballCount.o = 0
        matchInfo.ballCount.b = 0
        matchInfo.ballCount.s = 0
        matchInfo.runners.runnerIds.fill(null)

        matchInfo.isTop = !matchInfo.isTop
        if (matchInfo.isTop) matchInfo.inning++
    }
}