package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.game.InningState
import com.baseball.director.domain.game.PlayResult
import com.baseball.director.domain.game.PlayType
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BaseRunningService {

    fun processPlay(state: InningState, result: PlayResult, batter: Batter, tactic: String = "NORMAL") {

        state.scoreLog.clear()

        when (result.type) {
            PlayType.OUT -> {
                state.outCount++
                // ⭐ [추가] 아웃이지만 '외야 뜬공'이면 희생플라이 체크!
                if (state.outCount < 3) { // 3아웃이면 체크할 필요 없음
                    checkSacrificeFly(state, result, batter, tactic)
                }
            }
            PlayType.STRIKEOUT -> state.outCount++

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

            PlayType.ERROR -> advanceRunners(state, 1, batter, tactic)
            PlayType.WALK, PlayType.HIT_BY_PITCH -> pushRunners(state, batter)
            PlayType.HIT -> advanceRunners(state, result.hitType, batter, tactic)

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

    // =================================================================
    // 🕊️ [로직] 희생플라이 (Tag-up) 처리
    // =================================================================
    private fun checkSacrificeFly(state: InningState, result: PlayResult, batter: Batter, tactic: String) {
        // 1. 외야 뜬공인지 확인 (detail 문자열 분석)
        // 예: "중견수 뜬공 아웃" -> O, "유격수 뜬공 아웃" -> X, "좌익수 땅볼 아웃"(보살) -> X
        val detail = result.detail
        val isOutfield = detail.contains("좌익수") || detail.contains("중견수") || detail.contains("우익수")
        val isFlyBall = detail.contains("뜬공")

        if (!isOutfield || !isFlyBall) return // 내야 뜬공이나 땅볼이면 리턴

        // 2. 3루 주자 태그업 시도 (홈 쇄도)
        state.thirdBase?.let { runner ->
            val outcome = attemptTagUp(runner, batter, "HOME", tactic)
            when (outcome) {
                RunResult.SUCCESS -> {
                    scoreRunner(state, runner) // 득점!
                    state.thirdBase = null
                    state.scoreLog.add("🕊️ ${runner.name}, 희생플라이로 득점 성공!")
                }
                RunResult.OUT -> {
                    state.outCount++
                    state.thirdBase = null
                    state.scoreLog.add("🚨 ${runner.name}, 태그업 후 홈에서 횡사! (더블플레이)")
                }
                RunResult.HOLD -> {
                    // 뛰지 않음 (그대로 3루)
                }
            }
        }

        // 3. 2루 주자 태그업 시도 (3루 진루) - 아웃카운트가 늘어나서 3아웃이 되었는지 체크 필요
        if (state.outCount < 3) {
            state.secondBase?.let { runner ->
                // 3루가 비어있어야 뜀
                if (state.thirdBase == null) {
                    val outcome = attemptTagUp(runner, batter, "3RD", tactic)
                    when (outcome) {
                        RunResult.SUCCESS -> {
                            state.thirdBase = runner
                            state.secondBase = null
                            state.scoreLog.add("🏃 ${runner.name}, 과감한 태그업으로 3루 안착!")
                        }
                        RunResult.OUT -> {
                            state.outCount++
                            state.secondBase = null
                            state.scoreLog.add("🚨 ${runner.name}, 3루 가다가 아웃!")
                        }
                        RunResult.HOLD -> {} // 대기
                    }
                }
            }
        }
    }

    // [Helper] 태그업 성공 여부 판정
    private fun attemptTagUp(runner: Batter, batter: Batter, targetBase: String, tactic: String): RunResult {
        // 1. 기본 확률 = 주자의 발
        var successProb = runner.runSpeed.toDouble()

        // 2. 타자의 희생플라이(sf) 능력 반영
        // sf가 높을수록 타구를 멀리 보냈을 확률이 높음 (개당 2% 보너스)
        successProb += (batter.sf * 2.0)

        // 3. 거리 랜덤 변수 (외야수가 얼마나 깊은 곳에서 잡았나)
        // 0(아주 얕음) ~ 40(워닝트랙) 점수 추가
        val deepBonus = Random.nextInt(0, 40)
        successProb += deepBonus

        // 4. 난이도 페널티
        val penalty = if (targetBase == "HOME") 50 else 30 // 홈 승부가 더 어려움
        successProb -= penalty

        // 확률 보정 (0 ~ 100)
        successProb = successProb.coerceIn(5.0, 95.0)

        // 5. 뛸지 말지 결정 (Decision Threshold)
        // NORMAL: 70% 이상이어야 뜀 (안전주의)
        // AGGRESSIVE: 40%만 돼도 뜀 (공격주의)
        val threshold = if (tactic == "AGGRESSIVE_RUNNING") 40.0 else 70.0

        if (successProb < threshold) {
            return RunResult.HOLD
        }

        // 6. 결과 판정
        val dice = Random.nextDouble(0.0, 100.0)
        return if (dice < successProb) {
            RunResult.SUCCESS
        } else {
            RunResult.OUT
        }
    }

    // =================================================================
    // 👇 아래는 기존 로직 (안타 시 진루, 도루 등) - 그대로 유지
    // =================================================================

    private fun advanceRunners(state: InningState, hitType: Int, batter: Batter, tactic: String) {
        val runner3 = state.thirdBase
        val runner2 = state.secondBase
        val runner1 = state.firstBase

        state.thirdBase = null
        state.secondBase = null
        state.firstBase = null

        scoreRunner(state, runner3)

        if (runner2 != null) {
            when (hitType) {
                1 -> {
                    val result = attemptExtraAdvance(runner2, "HOME_ON_SINGLE", state.outCount, tactic)
                    when (result) {
                        RunResult.SUCCESS -> {
                            scoreRunner(state, runner2)
                            state.scoreLog.add("⚡ ${runner2.name}, 2루에서 홈까지 주루 성공!")
                        }
                        RunResult.OUT -> {
                            state.outCount++
                            state.scoreLog.add("🚨 ${runner2.name}, 홈 쇄도하다 태그 아웃!")
                        }
                        RunResult.HOLD -> state.thirdBase = runner2
                    }
                }
                else -> scoreRunner(state, runner2)
            }
        }

        if (runner1 != null) {
            when (hitType) {
                1 -> {
                    val result = attemptExtraAdvance(runner1, "3RD_ON_SINGLE", state.outCount, tactic)
                    when (result) {
                        RunResult.SUCCESS -> {
                            state.thirdBase = runner1
                            state.scoreLog.add("⚡ ${runner1.name}, 1루타에 3루까지!")
                        }
                        RunResult.OUT -> {
                            state.outCount++
                            state.scoreLog.add("🚨 ${runner1.name}, 3루 가다가 횡사!")
                        }
                        RunResult.HOLD -> state.secondBase = runner1
                    }
                }
                2 -> {
                    val result = attemptExtraAdvance(runner1, "HOME_ON_DOUBLE", state.outCount, tactic)
                    when (result) {
                        RunResult.SUCCESS -> {
                            scoreRunner(state, runner1)
                            state.scoreLog.add("⚡ ${runner1.name}, 2루타에 홈까지!")
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

        if (state.outCount < 3) {
            when (hitType) {
                1 -> state.firstBase = batter
                2 -> state.secondBase = batter
                3 -> state.thirdBase = batter
            }
        }
    }

    private enum class RunResult { SUCCESS, OUT, HOLD }

    private fun attemptExtraAdvance(runner: Batter, scenario: String, outCount: Int, tactic: String): RunResult {
        var successProb = runner.runSpeed
        val penalty = when (scenario) {
            "3RD_ON_SINGLE" -> 20
            "HOME_ON_SINGLE" -> 45
            "HOME_ON_DOUBLE" -> 30
            else -> 0
        }
        successProb -= penalty
        if (outCount == 2) successProb += 10
        successProb = successProb.coerceIn(5, 95)

        val threshold = if (tactic == "AGGRESSIVE_RUNNING") 40 else 70
        if (successProb < threshold) return RunResult.HOLD

        val dice = Random.nextInt(0, 100)
        return if (dice < successProb) RunResult.SUCCESS else RunResult.OUT
    }

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
            // state.scoreLog.add("${it.name} 득점!") // (중복 로그 방지 위해 여기서 로그는 뺄 수도 있음, 상황 봐서 조정)
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