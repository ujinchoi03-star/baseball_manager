package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.entity.Pitcher
import com.baseball.director.domain.game.PlayType
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class GameEngineServiceTest {

    private val gameEngineService = GameEngineService()

    @Test
    fun testPlayBall_Normal() {
        // 1. 더미 데이터 생성
        val pitcher = createDummyPitcher()
        val batter = createDummyBatter("타자")
        val catcher = createDummyBatter("포수")
        val defense = listOf(catcher, createDummyBatter("1루수"), createDummyBatter("2루수"))

        // 2. 실행 (업데이트된 파라미터 적용)
        val result = gameEngineService.playBall(
            pitcher = pitcher,
            batter = batter,
            catcher = catcher,       // ⭐ 포수 추가
            defensePlayers = defense,
            runner = null,           // ⭐ 주자 없음 (null)
            outCount = 0,
            currentPitchCount = 15,
            tactic = "NORMAL"
        )

        // 3. 검증
        println("일반 타격 결과: ${result.detail}")
        assertNotNull(result)
    }

    @Test
    fun testPlayBall_Steal() {
        val pitcher = createDummyPitcher()
        val batter = createDummyBatter("타자")
        val catcher = createDummyBatter("포수")
        val runner = createDummyBatter("주자") // 도루할 주자
        val defense = listOf(catcher)

        // 도루 시도
        val result = gameEngineService.playBall(
            pitcher = pitcher,
            batter = batter,
            catcher = catcher,
            defensePlayers = defense,
            runner = runner,         // ⭐ 주자 객체 전달
            outCount = 0,
            currentPitchCount = 15,
            tactic = "STEAL"         // ⭐ 도루 작전
        )

        println("도루 결과: ${result.detail}")
        // 결과가 도루 성공 혹은 실패여야 함
        assert(result.type == PlayType.STEAL_SUCCESS || result.type == PlayType.STEAL_FAIL)
    }

    // --- Helper Methods (더미 객체 생성기) ---

    private fun createDummyPitcher(): Pitcher {
        return Pitcher(
            id = 1L, name = "투수", team = "TeamA",
            ip = 0.0, h = 0, hr = 0, bb = 0, hbp = 0, so = 0,
            go = 0, ao = 0, gdp = 0, error = 0, fpct = 0.0
        )
    }

    private fun createDummyBatter(name: String): Batter {
        return Batter(
            id = 10L, name = name, team = "TeamB",
            pa=100, hit=30, doubleHit=5, tripleHit=1, homeRun=2,
            sac=0, sf=0, avg=0.300, strikeOut=10, walk=10, hbp=0, gdp=0,
            slg=0.4, obp=0.4, ops=0.8, phBa=0.0, go=10, ao=10,
            position="C", error=0, fpct=0.99, csPct=0.3, sb=5, cs=2
        )
    }
}