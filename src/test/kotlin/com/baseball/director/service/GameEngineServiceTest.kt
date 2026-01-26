package com.baseball.director.service

import org.junit.jupiter.api.Test
import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.entity.Pitcher
import com.baseball.director.domain.game.PlayType

class GameEngineServiceTest {

    private val gameEngine = GameEngineService()

    @Test
    fun `병살타_확률_테스트_100번`() {
        // 1. 땅볼 유도형 투수 (땅볼 1000개, 병살유도 100개)
        val pitcher = Pitcher(
            id = 1L, name = "땅볼맨", team = "한화",
            ip = 100.0, h = 100, hr = 0, bb = 10, hbp = 0, so = 50,
            go = 1000, ao = 10, gdp = 100, // 땅볼 압도적, 병살 유도 높음
            error = 0, fpct = 1.0
        )

        // 2. 병살타 제조기 타자 (땅볼 500개, 병살타 50개)
        val batter = Batter(
            id = 1L, name = "병살맨", team = "LG",
            pa = 600, hit = 150, doubleHit = 10, tripleHit = 0, homeRun = 5,
            sac = 0, sf = 0, avg = 0.250,
            strikeOut = 50, walk = 50, hbp = 0, gdp = 50, // 병살타 많음
            slg = 0.350, obp = 0.300, ops = 0.650, phBa = 0.0,
            go = 500, ao = 10, // 땅볼 성향 강함
            position = "지명타자", error = 0, fpct = 0.0, csPct = 0.0, sb = 0, cs = 0
        )

        // 3. 수비진 (수비율 100% - 에러 변수 제거)
        val defense = listOf(createDummyFielder("유격수", "유격수"))

        println("=== ⚾️ 병살타 집중 테스트 (무사 1루 상황 가정) ⚾️ ===")

        var gdpCount = 0

        for (i in 1..100) {
            // ⭐ [중요] isRunnerOnFirst = true, outCount = 0 으로 설정하여 병살 조건 충족
            val result = gameEngine.playBall(
                pitcher,
                batter,
                defense,
                isRunnerOnFirst = true,
                outCount = 0, currentPitchCount = 0
            )

            if (result.type == PlayType.GDP) {
                println("[Result $i] 🔥 ${result.detail} (병살타 발생!)")
                gdpCount++
            } else if (result.type == PlayType.OUT && result.detail.contains("땅볼")) {
                println("[Result $i] 땅볼 아웃 (병살 실패)")
            }
            // 안타나 다른 결과는 로그 생략 (너무 길어지니까)
        }

        println("=== 종료: 100번 중 병살타 ${gdpCount}번 발생 ===")
    }

    private fun createDummyFielder(name: String, pos: String): Batter {
        return Batter(
            id = 0L, name = name, team = "LG", pa = 0, hit = 0, doubleHit = 0, tripleHit = 0, homeRun = 0,
            sac = 0, sf = 0, avg = 0.0, strikeOut = 0, walk = 0, hbp = 0, gdp = 0,
            slg = 0.0, obp = 0.0, ops = 0.0, phBa = 0.0, go = 0, ao = 0,
            position = pos, error = 0, fpct = 1.0, csPct = 0.0, sb = 0, cs = 0
        )
    }
}