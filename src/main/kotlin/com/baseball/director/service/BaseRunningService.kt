package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.game.InningState
import com.baseball.director.domain.game.PlayResult
import com.baseball.director.domain.game.PlayType
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BaseRunningService {

    // ⭐ [수정] tactic 파라미터 추가!
    fun processPlay(state: InningState, result: PlayResult, batter: Batter, tactic: String = "NORMAL") {

        state.scoreLog.clear()

        when (result.type) {
            PlayType.OUT, PlayType.STRIKEOUT -> state.outCount++
            PlayType.STEAL_SUCCESS -> advanceStealRunner(state)
            PlayType.STEAL_FAIL -> {
                state.outCount++
                removeFailedRunner(state)
            }
            PlayType.SACRIFICE -> {
                state.outCount++
                pushRunnersOneBase(state)
            }
            PlayType.GDP -> {
                state.outCount += 2
                state.firstBase = null
                if (state.secondBase != null) {
                    state.thirdBase = state.secondBase
                    state.secondBase = null
                }
            }
            PlayType.ERROR -> advanceRunners(state, 1, batter, tactic) // ⭐ tactic 전달
            PlayType.WALK, PlayType.HIT_BY_PITCH -> pushRunners(state, batter)
            PlayType.HIT -> advanceRunners(state, result.hitType, batter, tactic) // ⭐ tactic 전달
            PlayType.HOMERUN -> {
                scoreRunner(state, state.thirdBase)
                scoreRunner(state, state.secondBase)
                scoreRunner(state, state.firstBase)
                scoreRunner(state, batter)
                state.firstBase = null
                state.secondBase = null
                state.thirdBase = null
            }
        }
    }

    // --- [핵심] 안타 시 주루 플레이 (전술 반영) ---
    private fun advanceRunners(state: InningState, hitType: Int, batter: Batter, tactic: String) {
        val runner3 = state.thirdBase
        val runner2 = state.secondBase
        val runner1 = state.firstBase

        // 베이스 초기화
        state.thirdBase = null
        state.secondBase = null
        state.firstBase = null

        // 1. 3루 주자 (무조건 득점)
        scoreRunner(state, runner3)

        // 2. 2루 주자 처리
        if (runner2 != null) {
            when (hitType) {
                1 -> {
                    // [상황: 1루타 때 2루 주자가 홈까지?]
                    // tactic에 따라 뛸지 말지 결정 + 결과(성공/아웃/멈춤) 리턴
                    val result = attemptExtraAdvance(runner2, "HOME_ON_SINGLE", state.outCount, tactic)
                    when (result) {
                        RunResult.SUCCESS -> {
                            scoreRunner(state, runner2)
                            state.scoreLog.add("⚡ ${runner2.name}, 2루에서 홈까지 과감한 주루 성공!")
                        }
                        RunResult.OUT -> {
                            state.outCount++
                            state.scoreLog.add("🚨 ${runner2.name}, 홈 쇄도하다 태그 아웃!")
                        }
                        RunResult.HOLD -> {
                            state.thirdBase = runner2 // 3루에서 멈춤
                        }
                    }
                }
                else -> scoreRunner(state, runner2) // 2루타 이상은 여유있게 득점
            }
        }

        // 3. 1루 주자 처리
        if (runner1 != null) {
            when (hitType) {
                1 -> {
                    // [상황: 1루타 때 1루 주자가 3루까지?]
                    val result = attemptExtraAdvance(runner1, "3RD_ON_SINGLE", state.outCount, tactic)
                    when (result) {
                        RunResult.SUCCESS -> {
                            state.thirdBase = runner1
                            state.scoreLog.add("⚡ ${runner1.name}, 1루타에 3루까지 전력 질주!")
                        }
                        RunResult.OUT -> {
                            state.outCount++
                            state.scoreLog.add("🚨 ${runner1.name}, 3루 가다가 횡사!")
                        }
                        RunResult.HOLD -> state.secondBase = runner1
                    }
                }
                2 -> {
                    // [상황: 2루타 때 1루 주자가 홈까지?]
                    val result = attemptExtraAdvance(runner1, "HOME_ON_DOUBLE", state.outCount, tactic)
                    when (result) {
                        RunResult.SUCCESS -> {
                            scoreRunner(state, runner1)
                            state.scoreLog.add("⚡ ${runner1.name}, 2루타에 홈까지 쇄도 성공!")
                        }
                        RunResult.OUT -> {
                            state.outCount++
                            state.scoreLog.add("🚨 ${runner1.name}, 홈에서 아웃!")
                        }
                        RunResult.HOLD -> state.thirdBase = runner1
                    }
                }
                3 -> scoreRunner(state, runner1)
            }
        }

        // 4. 타자 주자 배치 (아웃 카운트가 늘어나서 이닝이 끝났는지 체크 필요하지만, 일단 배치)
        // (단, 3아웃이면 점수/주자 모두 무효화되므로 GamePlayService에서 처리됨)
        if (state.outCount < 3) {
            when (hitType) {
                1 -> state.firstBase = batter
                2 -> state.secondBase = batter
                3 -> state.thirdBase = batter
            }
        }
    }

    // --- [Helper] 추가 진루 시도 판정 로직 (Risk vs Reward) ---
    private enum class RunResult { SUCCESS, OUT, HOLD }

    private fun attemptExtraAdvance(runner: Batter, scenario: String, outCount: Int, tactic: String): RunResult {
        // 1. 성공 확률 계산 (선수 능력치 기반)
        var successProb = runner.runSpeed

        val penalty = when (scenario) {
            "3RD_ON_SINGLE" -> 20  // 1루->3루 (중간 난이도)
            "HOME_ON_SINGLE" -> 45 // 2루->홈 (어려움)
            "HOME_ON_DOUBLE" -> 30 // 1루->홈 (할만함)
            else -> 0
        }
        successProb -= penalty

        // 2아웃이면 자동 출발하므로 확률 보정
        if (outCount == 2) successProb += 10

        // 확률 범위 보정 (최소 5%, 최대 95%)
        successProb = successProb.coerceIn(5, 95)

        // 2. ⭐ [핵심] 뛸지 말지 결정 (Threshold)
        // 적극적 주루(AGGRESSIVE_RUNNING)면 30%만 돼도 뜀, 보통은 60% 넘어야 뜀
        val threshold = if (tactic == "AGGRESSIVE_RUNNING") 40 else 70

        // 확률이 기준치보다 낮으면 -> 안전하게 멈춤 (HOLD)
        if (successProb < threshold) {
            return RunResult.HOLD
        }

        // 3. 뛰기로 결정함! -> 주사위 굴리기 (성공 vs 아웃)
        val dice = Random.nextInt(0, 100)
        return if (dice < successProb) {
            RunResult.SUCCESS // 세이프!
        } else {
            RunResult.OUT     // 아웃! (적극성의 대가)
        }
    }

    // --- 기존 로직 유지 ---
    private fun advanceStealRunner(state: InningState) {
        if (state.thirdBase != null) { scoreRunner(state, state.thirdBase); state.thirdBase = null }
        else if (state.secondBase != null) { state.thirdBase = state.secondBase; state.secondBase = null }
        else if (state.firstBase != null) { state.secondBase = state.firstBase; state.firstBase = null }
    }

    private fun removeFailedRunner(state: InningState) {
        if (state.thirdBase != null) state.thirdBase = null
        else if (state.secondBase != null) state.secondBase = null
        else if (state.firstBase != null) state.firstBase = null
    }

    private fun pushRunnersOneBase(state: InningState) {
        if (state.thirdBase != null) { scoreRunner(state, state.thirdBase); state.thirdBase = null }
        if (state.secondBase != null) { state.thirdBase = state.secondBase; state.secondBase = null }
        if (state.firstBase != null) { state.secondBase = state.firstBase; state.firstBase = null }
    }

    private fun pushRunners(state: InningState, batter: Batter) {
        if (state.firstBase != null) {
            if (state.secondBase != null) {
                if (state.thirdBase != null) scoreRunner(state, state.thirdBase)
                state.thirdBase = state.secondBase
            }
            state.secondBase = state.firstBase
        }
        state.firstBase = batter
    }

    private fun scoreRunner(state: InningState, runner: Batter?) {
        runner?.let {
            state.currentScore += 1
            state.scoreLog.add("${it.name} 득점!")
        }
    }
}

val Batter.runSpeed: Int
    get() {
        var speed = 60
        speed += (this.sb * 1).coerceAtMost(20)
        speed += (this.tripleHit * 3).coerceAtMost(15)
        speed -= (this.cs * 2).coerceAtMost(10)
        speed -= (this.gdp * 1).coerceAtMost(10)
        return speed.coerceIn(0, 100)
    }