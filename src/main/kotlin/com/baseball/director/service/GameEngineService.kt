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
        defensePlayers: List<Batter>,
        isRunnerOnFirst: Boolean,
        outCount: Int,
        currentPitchCount: Int
    ): PlayResult {

        // 1. 체력 페널티 계산
        val maxPitches = pitcher.maxPitchCount // 이제 에러 없이 잘 보입니다!
        var fatigueMultiplier = 1.0

        if (currentPitchCount > maxPitches) {
            val overPitches = currentPitchCount - maxPitches
            fatigueMultiplier = 1.0 + (overPitches * 0.005)
        }

        // 2. 투수 BF(상대 타자 수) 추정
        val pitcherBf = (pitcher.ip * 3 + pitcher.h + pitcher.bb + pitcher.hbp).coerceAtLeast(1.0)
        val dice = Random.nextDouble()

        // [Step 1] 사사구 / 삼진 / 홈런 (체력 반영)
        val probWalk = (((batter.walk + batter.hbp).toDouble() / batter.pa +
                (pitcher.bb + pitcher.hbp).toDouble() / pitcherBf) / 2) * fatigueMultiplier

        if (dice < probWalk) return PlayResult(PlayType.WALK, "볼넷/사구 출루")

        val probSo = ((batter.strikeOut.toDouble() / batter.pa + pitcher.so.toDouble() / pitcherBf) / 2) / fatigueMultiplier
        if (Random.nextDouble() < probSo) return PlayResult(PlayType.STRIKEOUT, "삼진 아웃")

        val probHr = (((batter.homeRun.toDouble() / batter.pa + pitcher.hr.toDouble() / pitcherBf) / 2) * fatigueMultiplier)
        if (Random.nextDouble() < probHr) return PlayResult(PlayType.HOMERUN, "${batter.name} 홈런!", 4)

        // [Step 2] 인플레이 타구
        val pitcherAvg = pitcher.h.toDouble() / pitcherBf
        val probHit = ((batter.avg + pitcherAvg) / 2) * fatigueMultiplier
        val isHit = Random.nextDouble() < probHit

        // [Step 3] 타구 질 결정
        var batterGoRatio = batter.go.toDouble() / (batter.go + batter.ao).coerceAtLeast(1)
        var pitcherGoRatio = pitcher.go.toDouble() / (pitcher.go + pitcher.ao).coerceAtLeast(1)

        if (fatigueMultiplier > 1.0) pitcherGoRatio /= fatigueMultiplier

        val probGround = (batterGoRatio + pitcherGoRatio) / 2
        val isGround = Random.nextDouble() < probGround
        val ballType = if (isGround) "땅볼" else "뜬공"

        // [Step 4] 수비수 선정
        val fielder = selectFielder(isGround, defensePlayers, pitcher, isHit)

        if (isHit) {
            return determineHitType(batter, fielder)
        } else {
            if (isGround && isRunnerOnFirst && outCount < 2) {
                val probGdp = (batter.gdp.toDouble() / batter.pa + pitcher.gdp.toDouble() / pitcherBf) / 2
                if (Random.nextDouble() < probGdp) {
                    if (checkError(fielder)) {
                        return PlayResult(PlayType.ERROR, "${fielder.position} ${fielder.name} 병살 코스에서 실책!", 1, fielder.name)
                    }
                    return PlayResult(PlayType.GDP, "${fielder.position} 앞 병살타!", 0, fielder.name)
                }
            }

            if (checkError(fielder)) {
                return PlayResult(PlayType.ERROR, "${fielder.position} ${fielder.name} 실책으로 출루", 1, fielder.name)
            } else {
                return PlayResult(PlayType.OUT, "${fielder.position} ${ballType} 아웃", 0, fielder.name)
            }
        }
    }

    // --- Helper 함수들 ---
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

        if (selectedPos == "투수") {
            return convertPitcherToFielder(pitcher)
        }

        return defense.find { it.position == selectedPos }
            ?: defense.firstOrNull()
            ?: convertPitcherToFielder(pitcher)
    }

    private fun convertPitcherToFielder(p: Pitcher): Batter {
        return Batter(
            id = null, name = p.name, team = p.team,
            pa = 0, hit = 0, doubleHit = 0, tripleHit = 0, homeRun = 0,
            sac = 0, sf = 0, avg = 0.0, strikeOut = 0, walk = 0, hbp = 0, gdp = 0,
            slg = 0.0, obp = 0.0, ops = 0.0, phBa = 0.0, go = 0, ao = 0,
            position = "투수", error = p.error, fpct = p.fpct, csPct = 0.0, sb = 0, cs = 0
        )
    }

    private fun determineHitType(batter: Batter, fielder: Batter): PlayResult {
        val hitDice = Random.nextDouble()
        val totalHits = batter.hit.coerceAtLeast(1).toDouble()
        val prob2B = batter.doubleHit / totalHits
        val prob3B = batter.tripleHit / totalHits

        return when {
            hitDice < prob3B -> PlayResult(PlayType.HIT, "우중간을 가르는 3루타!", 3, fielder.name)
            hitDice < prob3B + prob2B -> PlayResult(PlayType.HIT, "좌익선상 2루타!", 2, fielder.name)
            else -> PlayResult(PlayType.HIT, "${fielder.position} 앞 1루타", 1, fielder.name)
        }
    }

    private fun checkError(fielder: Batter): Boolean {
        val successRate = if (fielder.fpct > 0.0) fielder.fpct else 0.970
        return Random.nextDouble() > successRate
    }

} // ⬅️ 클래스 끝나는 괄호가 여기 있습니다!

// ---------------------------------------------------------
// ⭐ [중요] maxPitchCount는 클래스 밖(파일 레벨)에 있어야
// 다른 파일(GameDirectorService)에서도 볼 수 있습니다.
// ---------------------------------------------------------
val Pitcher.maxPitchCount: Int
    get() {
        val calculated = 15 + (this.ip * 0.55).toInt()
        return calculated.coerceIn(40, 110)
    }