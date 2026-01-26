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

    // ⭐ 어벤져스(사용자님이 만든 로직) 서비스 주입
    private val gameEngineService: GameEngineService,
    private val baseRunningService: BaseRunningService
) {

    @Transactional
    fun handleAction(message: GameMessage): String {
        // 1. 경기 정보 찾기
        val matchInfo = matchInfoRepository.findById(message.matchId)
            .orElseThrow { IllegalArgumentException("없는 경기입니다.") }

        // 2. 공격/수비 라인업 가져오기
        val isTop = matchInfo.isTop
        val attackLineup = if (isTop) matchInfo.awayLineup else matchInfo.homeLineup
        val defenseLineup = if (isTop) matchInfo.homeLineup else matchInfo.awayLineup

        // 3. 현재 타자와 투수 찾기 (DB ID -> 실제 객체 변환)
        // (임시: 타순 1번과 선발투수로 고정, 나중에 index 기능 추가 필요)
        val currentBatterId = attackLineup.battingOrder.firstOrNull() ?: 1L
        val currentPitcherId = defenseLineup.starters["P"] ?: 1L

        val batter = batterRepository.findById(currentBatterId).orElseThrow()
        val pitcher = pitcherRepository.findById(currentPitcherId).orElseThrow()

        // 수비수 리스트 (엔진용)
        val defenseIds = defenseLineup.starters.values.toList()
        val defensePlayers = batterRepository.findAllById(defenseIds)

        // 4. ⭐ [Adapter 패턴] DB 데이터(MatchInfo) -> 로직용(InningState) 변환
        // MatchInfo는 ID만 가지고 있고, InningState는 객체를 원하니까 변환해줍니다.
        val state = InningState(
            outCount = matchInfo.ballCount.o,
            // currentScore에 현재 '총 점수'를 넣어서 로직이 점수를 더할 수 있게 함
            currentScore = if (isTop) matchInfo.score.away else matchInfo.score.home
        ).apply {
            // 주자 ID 리스트 -> 실제 Batter 객체로 변환해서 세팅
            firstBase = matchInfo.runners.runnerIds[0]?.let { batterRepository.findById(it).orElse(null) }
            secondBase = matchInfo.runners.runnerIds[1]?.let { batterRepository.findById(it).orElse(null) }
            thirdBase = matchInfo.runners.runnerIds[2]?.let { batterRepository.findById(it).orElse(null) }
        }

        var resultMessage = ""

        if (message.type == "PITCH") {
            // 5. 🎲 게임 엔진 가동! (확률 계산)
            // (투구수는 임시로 이닝 * 15로 추정)
            val estimatedPitchCount = matchInfo.inning * 15

            val playResult = gameEngineService.playBall(
                pitcher = pitcher,
                batter = batter,
                defensePlayers = defensePlayers,
                isRunnerOnFirst = state.firstBase != null,
                outCount = state.outCount,
                currentPitchCount = estimatedPitchCount
            )

            // 6. 🏃 주루 플레이 가동! (점수 계산 & 이동)
            // 여기서 state 안의 점수와 주자 위치가 바뀝니다.
            baseRunningService.processPlay(state, playResult, batter)

            // 7. 결과 반영: 로직 데이터(InningState) -> 다시 DB(MatchInfo)
            matchInfo.ballCount.o = state.outCount

            // 주자 객체 -> 다시 ID로 변환해서 저장
            matchInfo.runners.runnerIds[0] = state.firstBase?.id
            matchInfo.runners.runnerIds[1] = state.secondBase?.id
            matchInfo.runners.runnerIds[2] = state.thirdBase?.id

            // 점수 업데이트
            if (isTop) matchInfo.score.away = state.currentScore
            else matchInfo.score.home = state.currentScore

            // 볼카운트 리셋 (타격 결과가 나왔으므로)
            if (playResult.type != com.baseball.director.domain.game.PlayType.STRIKEOUT) { // 삼진 아닐 때만
                matchInfo.ballCount.b = 0
                matchInfo.ballCount.s = 0
            }

            // 결과 메시지 조합
            resultMessage = "⚾ ${playResult.detail}"
            if (state.scoreLog.isNotEmpty()) {
                resultMessage += "\n👏 " + state.scoreLog.joinToString(", ")
            }

            // 8. 3아웃 공수교대 체크
            if (matchInfo.ballCount.o >= 3) {
                changeInning(matchInfo)
                resultMessage += "\n🔄 3아웃 공수교대! (${matchInfo.inning}회)"
            }
        } else if (message.type == "CHAT") {
            resultMessage = "[채팅] ${message.senderId}: ${message.content}"
        }

        // 9. 최종 저장
        matchInfoRepository.save(matchInfo)
        return resultMessage
    }

    // 공수교대 헬퍼 함수
    private fun changeInning(matchInfo: com.baseball.director.domain.entity.MatchInfo) {
        matchInfo.ballCount.o = 0
        matchInfo.ballCount.b = 0
        matchInfo.ballCount.s = 0
        matchInfo.runners.runnerIds.fill(null) // 주자 싹 지우기

        matchInfo.isTop = !matchInfo.isTop // 초 <-> 말
        if (matchInfo.isTop) matchInfo.inning++ // 다시 초가 되면 이닝 증가
    }
}