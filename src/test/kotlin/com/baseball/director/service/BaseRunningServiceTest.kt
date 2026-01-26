package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.game.InningState
import com.baseball.director.domain.game.PlayResult
import com.baseball.director.domain.game.PlayType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BaseRunningServiceTest {

    private val baseRunningService = BaseRunningService()

    // 더미 선수 생성 헬퍼
    private fun createPlayer(name: String) = Batter(
        id = 1L, name = name, team = "TEST", pa = 0, hit = 0, doubleHit = 0, tripleHit = 0, homeRun = 0,
        sac = 0, sf = 0, avg = 0.0, strikeOut = 0, walk = 0, hbp = 0, gdp = 0,
        slg = 0.0, obp = 0.0, ops = 0.0, phBa = 0.0, go = 0, ao = 0,
        position = "타자", error = 0, fpct = 0.0, csPct = 0.0, sb = 0, cs = 0
    )

    @Test
    fun `만루_홈런_테스트`() {
        // 상황: 만루
        val state = InningState()
        state.firstBase = createPlayer("주자1")
        state.secondBase = createPlayer("주자2")
        state.thirdBase = createPlayer("주자3")

        val batter = createPlayer("타자_이승엽")
        val homeRunResult = PlayResult(PlayType.HOMERUN, "홈런!", 4)

        // 실행
        baseRunningService.processPlay(state, homeRunResult, batter)

        // 검증
        assertEquals(4, state.currentScore) // 4점 득점?
        assertNull(state.firstBase)         // 주자들 다 없어졌나?
        assertNull(state.secondBase)
        assertNull(state.thirdBase)
        println("만루홈런 로그: ${state.scoreLog}")
    }

    @Test
    fun `밀어내기_볼넷_테스트`() {
        // 상황: 1,2,3루 만루
        val state = InningState()
        state.firstBase = createPlayer("주자1")
        state.secondBase = createPlayer("주자2")
        state.thirdBase = createPlayer("주자3")

        val batter = createPlayer("타자_홍창기")
        val walkResult = PlayResult(PlayType.WALK, "볼넷")

        // 실행
        baseRunningService.processPlay(state, walkResult, batter)

        // 검증
        assertEquals(1, state.currentScore) // 1점 들어왔나? (3루 주자 득점)
        assertEquals("주자2", state.thirdBase?.name) // 2루주자가 3루로?
        assertEquals("주자1", state.secondBase?.name) // 1루주자가 2루로?
        assertEquals("타자_홍창기", state.firstBase?.name) // 타자가 1루로?
        println("밀어내기 로그: ${state.scoreLog}")
    }

    @Test
    fun `2루타_시_주자_이동_테스트`() {
        // 상황: 1루에 주자 있음
        val state = InningState()
        state.firstBase = createPlayer("주자1_빠른발")

        val batter = createPlayer("타자_김현수")
        val doubleHit = PlayResult(PlayType.HIT, "2루타", 2)

        // 실행
        baseRunningService.processPlay(state, doubleHit, batter)

        // 검증
        // 1루 주자는 3루까지 갔어야 함
        assertEquals("주자1_빠른발", state.thirdBase?.name)
        // 타자는 2루에 있어야 함
        assertEquals("타자_김현수", state.secondBase?.name)
        // 1루는 비어있어야 함
        assertNull(state.firstBase)
        // 득점은 없어야 함
        assertEquals(0, state.currentScore)
    }

    @Test
    fun `병살타_발생시_아웃2개_주자삭제_테스트`() {
        // [상황]: 무사 1루
        val state = InningState()
        state.outCount = 0
        state.firstBase = createPlayer("느린_주자") // 1루 주자

        val batter = createPlayer("병살타자")
        val gdpResult = PlayResult(PlayType.GDP, "유격수 앞 병살타")

        println("--- 병살타 전: 아웃 ${state.outCount}, 1루주자 ${state.firstBase?.name} ---")

        // [실행]
        baseRunningService.processPlay(state, gdpResult, batter)

        println("--- 병살타 후: 아웃 ${state.outCount}, 1루주자 ${state.firstBase?.name} ---")

        // [검증]
        assertEquals(2, state.outCount) // 아웃카운트가 0 -> 2가 되었는가?
        assertNull(state.firstBase)     // 1루 주자가 사라졌는가?
        assertNull(state.secondBase)    // 타자도 1루에 못 나갔으니 2루 등은 비어있어야 함
    }
} // <--- ⭐ 이 괄호가 없어서 에러가 났던 겁니다!