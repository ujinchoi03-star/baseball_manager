package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.entity.Pitcher
import com.baseball.director.domain.game.PlayResult
import com.baseball.director.domain.game.PlayType
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class GameEngineService {

    fun playBall(
        pitcher: Pitcher,
        batter: Batter,
        catcher: Batter,     // ⭐ [추가] 포수 (도루 저지율용)
        defensePlayers: List<Batter>,
        runner: Batter?,     // ⭐ [추가] 도루 시도하는 주자 (없으면 null)
        outCount: Int,
        currentPitchCount: Int,
        tactic: String = "NORMAL"
    ): PlayResult {

        // ==========================================
        // 1. 작전(Tactic) 처리 구역
        // ==========================================

        // 🏃 [작전 1] 도루 (Steal)
        if (tactic == "STEAL") {
            if (runner == null) {
                // 주자가 없는데 도루 작전? -> 그냥 일반 타격으로 넘김
                return playNormalBatting(pitcher, batter, defensePlayers, false, outCount, currentPitchCount)
            }

            // [도루 확률 공식]
            // 1. 주자의 도루 성공률 계산 (데이터가 없으면 기본 50% 가정)
            val totalAttempts = runner.sb + runner.cs
            val runnerSbRate = if (totalAttempts > 0) {
                runner.sb.toDouble() / totalAttempts
            } else {
                0.5 // 기록 없으면 반반
            }

            // 2. 포수의 도루 저지율 (csPct)
            val catcherCsRate = catcher.csPct

            // 3. 최종 성공 확률 계산
            // 기본 50% + (주자 능력 * 0.4) - (포수 능력 * 0.4)
            // 예: 주자(70%) vs 포수(30%) -> 0.5 + 0.28 - 0.12 = 0.66 (66%)
            var probSteal = 0.5 + (runnerSbRate * 0.4) - (catcherCsRate * 0.4)

            // 확률이 너무 낮거나 높지 않게 보정 (10% ~ 90%)
            probSteal = probSteal.coerceIn(0.1, 0.9)

            // 4. 주사위 굴리기
            val dice = Random.nextDouble()
            return if (dice < probSteal) {
                PlayResult(PlayType.STEAL_SUCCESS, "${runner.name} 도루 성공!", 0)
            } else {
                PlayResult(PlayType.STEAL_FAIL, "${runner.name} 도루 실패! 태그 아웃", 0)
            }
        }

        // 🏳️ [작전 2] 고의사구
        if (tactic == "INTENTIONAL_WALK") {
            return PlayResult(PlayType.WALK, "고의사구 (작전 지시)", 1)
        }

        // 🎋 [작전 3] 희생번트
        if (tactic == "BUNT") {
            val baseSuccessRate = 0.50
            val skillBonus = (batter.sac * 0.02) // 희생번트 1개당 2% 상승
            val probBuntSuccess = (baseSuccessRate + skillBonus).coerceAtMost(0.90)

            if (Random.nextDouble() < probBuntSuccess) {
                return PlayResult(PlayType.SACRIFICE, "희생번트 성공!", 0)
            } else {
                // 실패 시 50% 확률로 삼진, 50% 확률로 뜬공
                return if (Random.nextDouble() < 0.5) {
                    PlayResult(PlayType.STRIKEOUT, "번트 실패 (삼진)", 0)
                } else {
                    PlayResult(PlayType.OUT, "번트 실패 (뜬공)", 0)
                }
            }
        }

        // 작전 없으면 일반 타격
        return playNormalBatting(pitcher, batter, defensePlayers, runner != null, outCount, currentPitchCount)
    }

    // --- 기존 일반 타격 로직 (분리함) ---
    private fun playNormalBatting(
        pitcher: Pitcher,
        batter: Batter,
        defensePlayers: List<Batter>,
        isRunnerOnFirst: Boolean,
        outCount: Int,
        currentPitchCount: Int
    ): PlayResult {
        // 기존 코드 그대로 유지
        val maxPitches = pitcher.maxPitchCount
        var fatigueMultiplier = 1.0
        if (currentPitchCount > maxPitches) {
            fatigueMultiplier = 1.0 + ((currentPitchCount - maxPitches) * 0.005)
        }

        val pitcherBf = (pitcher.ip * 3 + pitcher.h + pitcher.bb + pitcher.hbp).coerceAtLeast(1.0)
        val dice = Random.nextDouble()

        val probWalk = (((batter.walk + batter.hbp).toDouble() / batter.pa + (pitcher.bb + pitcher.hbp).toDouble() / pitcherBf) / 2) * fatigueMultiplier
        if (dice < probWalk) return PlayResult(PlayType.WALK, "볼넷/사구 출루", 1)

        val probSo = ((batter.strikeOut.toDouble() / batter.pa + pitcher.so.toDouble() / pitcherBf) / 2) / fatigueMultiplier
        if (Random.nextDouble() < probSo) return PlayResult(PlayType.STRIKEOUT, "삼진 아웃", 0)

        val probHr = (((batter.homeRun.toDouble() / batter.pa + pitcher.hr.toDouble() / pitcherBf) / 2) * fatigueMultiplier)
        if (Random.nextDouble() < probHr) return PlayResult(PlayType.HOMERUN, "${batter.name} 홈런!", 4)

        val pitcherAvg = pitcher.h.toDouble() / pitcherBf
        val probHit = ((batter.avg + pitcherAvg) / 2) * fatigueMultiplier
        val isHit = Random.nextDouble() < probHit

        var batterGoRatio = batter.go.toDouble() / (batter.go + batter.ao).coerceAtLeast(1)
        var pitcherGoRatio = pitcher.go.toDouble() / (pitcher.go + pitcher.ao).coerceAtLeast(1)
        if (fatigueMultiplier > 1.0) pitcherGoRatio /= fatigueMultiplier

        val probGround = (batterGoRatio + pitcherGoRatio) / 2
        val isGround = Random.nextDouble() < probGround
        val ballType = if (isGround) "땅볼" else "뜬공"

        val fielder = selectFielder(isGround, defensePlayers, pitcher, isHit)

        if (isHit) {
            return determineHitType(batter, fielder)
        } else {
            if (isGround && isRunnerOnFirst && outCount < 2) {
                val probGdp = (batter.gdp.toDouble() / batter.pa + pitcher.gdp.toDouble() / pitcherBf) / 2
                if (Random.nextDouble() < probGdp) {
                    if (checkError(fielder)) return PlayResult(PlayType.ERROR, "${fielder.position} ${fielder.name} 실책!", 1, fielder.name)
                    return PlayResult(PlayType.GDP, "병살타!", 0, fielder.name)
                }
            }
            if (checkError(fielder)) {
                return PlayResult(PlayType.ERROR, "실책 출루", 1, fielder.name)
            } else {
                return PlayResult(PlayType.OUT, "$ballType 아웃", 0, fielder.name)
            }
        }
    }

    // Helper 함수들 (기존 그대로)
    private fun selectFielder(isGround: Boolean, defense: List<Batter>, pitcher: Pitcher, isHit: Boolean): Batter {
        val positionPool = if (isGround) {
            if (isHit) {
                if (Random.nextDouble() < 0.6) listOf("좌익수", "중견수", "우익수")
                else listOf("포수", "1루수", "2루수", "3루수", "유격수")
            } else {
                if (Random.nextDouble() < 0.85) listOf("투수", "포수", "1루수", "2루수", "3루수", "유격수")
                else listOf("좌익수", "중견수", "우익수")
            }
        } else {
            if (Random.nextDouble() < 0.9) listOf("좌익수", "중견수", "우익수")
            else listOf("포수", "1루수", "2루수", "3루수", "유격수")
        }
        val selectedPos = positionPool.random()
        if (selectedPos == "투수") return convertPitcherToFielder(pitcher)
        return defense.find { it.position == selectedPos } ?: defense.firstOrNull() ?: convertPitcherToFielder(pitcher)
    }

    private fun convertPitcherToFielder(p: Pitcher): Batter {
        // 투수를 Batter 객체로 변환 (수비용)
        return Batter(null, p.name, p.team, 0,0,0,0,0,0,0,0.0,0,0,0,0,0.0,0.0,0.0,0.0,0,0,"투수",p.error,p.fpct,0.0,0,0)
    }

    private fun determineHitType(batter: Batter, fielder: Batter): PlayResult {
        val hitDice = Random.nextDouble()
        val totalHits = batter.hit.coerceAtLeast(1).toDouble()
        val prob2B = batter.doubleHit / totalHits
        val prob3B = batter.tripleHit / totalHits
        return when {
            hitDice < prob3B -> PlayResult(PlayType.HIT, "3루타!", 3, fielder.name)
            hitDice < prob3B + prob2B -> PlayResult(PlayType.HIT, "2루타!", 2, fielder.name)
            else -> PlayResult(PlayType.HIT, "1루타", 1, fielder.name)
        }
    }

    private fun checkError(fielder: Batter): Boolean {
        val successRate = if (fielder.fpct > 0.0) fielder.fpct else 0.970
        return Random.nextDouble() > successRate
    }
}

val Pitcher.maxPitchCount: Int get() = (15 + (this.ip * 0.55).toInt()).coerceIn(40, 110)