package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.game.InningState
import com.baseball.director.domain.game.PlayResult
import com.baseball.director.domain.game.PlayType
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class BaseRunningService {

    // 타석 결과를 받아서 -> 주자를 이동시키고 -> 점수를 냄
    fun processPlay(state: InningState, result: PlayResult, batter: Batter) {

        // 로그 초기화 (이번 타석 득점자만 기록하기 위해)
        state.scoreLog.clear()

        when (result.type) {
            PlayType.OUT, PlayType.STRIKEOUT -> {
                state.outCount++
            }

            PlayType.GDP -> {
                // 1. 아웃카운트 2개 증가
                state.outCount += 2

                // 2. 주자 정리 (가장 일반적인 6-4-3 병살 가정)
                state.firstBase = null // 1루 주자 아웃

                // 2루 주자가 있었다면 3루로 진루 (병살 타구 처리하는 동안 이동)
                if (state.secondBase != null) {
                    state.thirdBase = state.secondBase
                    state.secondBase = null
                }
            }

            PlayType.ERROR -> {
                // 실책은 1루타와 비슷하게 처리 (추가 진루 로직 적용)
                advanceRunners(state, 1, batter)
            }

            PlayType.WALK, PlayType.HIT_BY_PITCH -> {
                // 밀어내기 로직 (Force Play) - 볼넷은 추가 진루 없음
                pushRunners(state, batter)
            }

            PlayType.HIT -> {
                // 안타 종류(1,2,3루타)에 따라 진루 + [추가 진루 시도]
                advanceRunners(state, result.hitType, batter)
            }

            PlayType.HOMERUN -> {
                // 싹쓸이 (이제 에러가 나지 않습니다!)
                scoreRunner(state, state.thirdBase)
                scoreRunner(state, state.secondBase)
                scoreRunner(state, state.firstBase)
                scoreRunner(state, batter) // 타자 본인 득점

                // 베이스 초기화
                state.firstBase = null
                state.secondBase = null
                state.thirdBase = null
            }
        }
    }

    // --- [로직 1] 볼넷/사구: 밀어내기 (Force) ---
    private fun pushRunners(state: InningState, batter: Batter) {
        if (state.firstBase != null) {
            if (state.secondBase != null) {
                if (state.thirdBase != null) {
                    scoreRunner(state, state.thirdBase)
                }
                state.thirdBase = state.secondBase
            }
            state.secondBase = state.firstBase
        }
        state.firstBase = batter
    }

    // --- [로직 2] 안타: 주력에 따른 리얼한 주루 플레이 (Active Running) ---
    private fun advanceRunners(state: InningState, hitType: Int, batter: Batter) {
        // 기존 주자들을 임시 변수에 담음 (이동 중 충돌 방지)
        // 지역 변수(val)에 담으면 스마트 캐스팅이 잘 됩니다.
        val runner3 = state.thirdBase
        val runner2 = state.secondBase
        val runner1 = state.firstBase

        // 베이스판을 일단 비움 (재배치 예정)
        state.thirdBase = null
        state.secondBase = null
        state.firstBase = null

        // 1. 3루 주자 처리 (안타면 무조건 득점)
        scoreRunner(state, runner3)

        // 2. 2루 주자 처리
        if (runner2 != null) {
            when (hitType) {
                1 -> {
                    // [1루타]: 원래는 3루까지지만, 발 빠르면 홈까지 쇄도!
                    if (tryExtraAdvance(runner2, "HOME_ON_SINGLE", state.outCount)) {
                        scoreRunner(state, runner2) // 홈인!
                        state.scoreLog.add("${runner2.name} 2루에서 홈까지 질주!")
                    } else {
                        state.thirdBase = runner2 // 안전하게 3루 멈춤
                    }
                }
                else -> scoreRunner(state, runner2) // 2루타 이상은 여유 있게 득점
            }
        }

        // 3. 1루 주자 처리
        if (runner1 != null) {
            when (hitType) {
                1 -> {
                    // [1루타]: 원래는 2루까지지만, 발 빠르면 3루까지!
                    if (tryExtraAdvance(runner1, "3RD_ON_SINGLE", state.outCount)) {
                        state.thirdBase = runner1 // 1루 -> 3루 점프!
                        state.scoreLog.add("${runner1.name} 1루타에 3루까지!")
                    } else {
                        state.secondBase = runner1 // 안전하게 2루
                    }
                }
                2 -> {
                    // [2루타]: 원래는 3루까지지만, 발 빠르면 홈까지!
                    if (tryExtraAdvance(runner1, "HOME_ON_DOUBLE", state.outCount)) {
                        scoreRunner(state, runner1) // 1루 -> 홈 점프!
                        state.scoreLog.add("${runner1.name} 2루타에 홈까지 쇄도!")
                    } else {
                        state.thirdBase = runner1 // 안전하게 3루
                    }
                }
                3 -> scoreRunner(state, runner1) // 3루타는 무조건 득점
            }
        }

        // 4. 타자 주자 배치
        when (hitType) {
            1 -> state.firstBase = batter
            2 -> state.secondBase = batter
            3 -> state.thirdBase = batter
        }
    }

    // --- [Helper] 추가 진루 성공 여부 판단 (확률 판정 방식) ---
    private fun tryExtraAdvance(runner: Batter, scenario: String, outCount: Int): Boolean {
        // 1. 기본 성공 확률 = 선수의 주력 (0 ~ 100%)
        var successProb = runner.runSpeed

        // 2. 상황별 페널티 (난이도가 높을수록 확률을 많이 깎음)
        // 예: 주력 100인 선수도 '2루->홈'은 -50% 페널티를 받아 성공률 50%가 됨
        val penalty = when (scenario) {
            "3RD_ON_SINGLE" -> 20  // 1루->3루 (난이도 하: 확률 -20% 깎음)
            "HOME_ON_SINGLE" -> 50 // 2루->홈 (난이도 상: 확률 -50% 깎음)
            "HOME_ON_DOUBLE" -> 30 // 1루->홈 (난이도 중: 확률 -30% 깎음)
            else -> 0
        }
        successProb -= penalty

        // 3. 상황별 보너스 (2아웃이면 무조건 뛰므로 확률 상승)
        if (outCount == 2) successProb += 15

        // 4. 확률의 최소/최대 보정 (최소 5%, 최대 95%로 제한)
        // 아무리 느려도 5%의 기적, 아무리 빨라도 5%의 실수 가능성 열어둠
        successProb = successProb.coerceIn(5, 95)

        // 5. 운명의 주사위 굴리기 (0 ~ 99)
        val dice = Random.nextInt(0, 100)

        // 주사위 값이 내 성공 확률보다 낮으면 성공! (예: 확률 70%면, 0~69 나오면 성공)
        return dice < successProb
    }

    // --- [Helper] 득점 처리 (⭐ 수정된 부분) ---
    // 파라미터를 'Batter?' (널 가능)으로 변경하여 에러 해결!
    private fun scoreRunner(state: InningState, runner: Batter?) {
        // runner가 null이 아닐 때만 실행 (.let)
        runner?.let {
            state.currentScore += 1
            state.scoreLog.add("${it.name} 득점! (팀 ${state.currentScore}점째)")
        }
    }
}

// Batter 엔티티에 대한 확장 함수 (주력 계산기)
val Batter.runSpeed: Int
    get() {
        var speed = 60
        speed += (this.sb * 1).coerceAtMost(20)
        speed += (this.tripleHit * 3).coerceAtMost(15)
        speed -= (this.cs * 2).coerceAtMost(10)
        speed -= (this.gdp * 1).coerceAtMost(10)
        return speed.coerceIn(0, 100)
    }