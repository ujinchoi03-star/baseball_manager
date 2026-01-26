package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.entity.Pitcher
import com.baseball.director.domain.game.GameResult
import com.baseball.director.domain.game.InningState
import org.springframework.stereotype.Service

@Service
class GameDirectorService(
    private val gameEngine: GameEngineService,
    private val baseRunning: BaseRunningService
) {
    fun playGame(
        homeTeamName: String, homePitcher: Pitcher, homeLineup: List<Batter>,
        awayTeamName: String, awayPitcher: Pitcher, awayLineup: List<Batter>
    ): GameResult {

        var homeScore = 0
        var awayScore = 0
        var nextHomeBatterIdx = 0
        var nextAwayBatterIdx = 0
        val scoreBoard = mutableMapOf<Int, Pair<Int, Int>>()

        // 투구수 추적 변수
        var homePitcherPitches = 0
        var awayPitcherPitches = 0

        println("🏟️ 경기 시작: $awayTeamName (원정) vs $homeTeamName (홈)")
        println("--------------------------------------------------")

        for (inning in 1..12) {
            if (inning > 9 && homeScore != awayScore) break

            // --- [초] 원정팀 공격 (수비: 홈팀, 투수: 홈팀 선발) ---
            println("\n=== $inning 회 초 [$awayTeamName 공격] ===")
            val awayInningResult = playHalfInning(
                pitcher = homePitcher,
                currentPitchCount = homePitcherPitches, // 현재 투구수 전달
                batters = awayLineup,
                defense = homeLineup, // 수비 명단 전달
                startBatterIdx = nextAwayBatterIdx,
                currentTeamScore = awayScore,
                targetScore = null
            )
            awayScore += awayInningResult.score
            nextAwayBatterIdx = awayInningResult.nextBatterIdx

            // 홈팀 투수 투구수 누적 및 체크
            homePitcherPitches += awayInningResult.pitchesThrown
            if (homePitcherPitches > homePitcher.maxPitchCount) {
                println("⚠️ 홈팀 투수 체력 저하! (투구수: $homePitcherPitches / 한계: ${homePitcher.maxPitchCount})")
            }

            // 9회말 생략 조건
            if (inning >= 9 && homeScore > awayScore) {
                scoreBoard[inning] = Pair(awayInningResult.score, -1)
                println("\n=== $inning 회 말 생략 (홈팀 리드) ===")
                break
            }

            // --- [말] 홈팀 공격 (수비: 원정팀, 투수: 원정팀 선발) ---
            println("\n=== $inning 회 말 [$homeTeamName 공격] ===")
            val homeInningResult = playHalfInning(
                pitcher = awayPitcher,
                currentPitchCount = awayPitcherPitches, // ⭐ 여기도 추가해야 함!
                batters = homeLineup,
                defense = awayLineup, // 수비 명단 전달
                startBatterIdx = nextHomeBatterIdx,
                currentTeamScore = homeScore,
                targetScore = if (inning >= 9) awayScore else null
            )
            homeScore += homeInningResult.score
            nextHomeBatterIdx = homeInningResult.nextBatterIdx

            // 원정팀 투수 투구수 누적 및 체크
            awayPitcherPitches += homeInningResult.pitchesThrown
            if (awayPitcherPitches > awayPitcher.maxPitchCount) {
                println("⚠️ 원정팀 투수 체력 저하! (투구수: $awayPitcherPitches / 한계: ${awayPitcher.maxPitchCount})")
            }

            scoreBoard[inning] = Pair(awayInningResult.score, homeInningResult.score)
            println("--- $inning 회 종료 스코어 | $awayTeamName $awayScore : $homeScore $homeTeamName ---")

            if (inning >= 9 && homeScore > awayScore) {
                println("🎉 홈팀 끝내기 승리!")
                break
            }
        }

        val winner = when {
            homeScore > awayScore -> homeTeamName
            awayScore > homeScore -> awayTeamName
            else -> "Draw"
        }

        return GameResult(homeTeamName, awayTeamName, homeScore, awayScore, winner, 0, scoreBoard)
    }

    private fun playHalfInning(
        pitcher: Pitcher,
        currentPitchCount: Int, // ⭐ 이 파라미터가 꼭 있어야 합니다!
        batters: List<Batter>,
        defense: List<Batter>,
        startBatterIdx: Int,
        currentTeamScore: Int,
        targetScore: Int?
    ): HalfInningResult {

        val state = InningState()
        var batterIdx = startBatterIdx
        var inningScore = 0

        // 이번 이닝에 던진 공 개수
        var pitchesInInning = 0

        while (state.outCount < 3) {
            val currentBatter = batters[batterIdx]

            // 타석당 3~6구 던짐 (랜덤)
            val pitchesForBatter = (3..6).random()
            pitchesInInning += pitchesForBatter

            // 엔진에 보낼 때는 [기존 누적 + 이번 이닝 누적] 합쳐서 보냄
            val totalPitchesNow = currentPitchCount + pitchesInInning

            val isRunnerOnFirst = state.firstBase != null

            // ⭐ playBall에 투구수(totalPitchesNow) 전달!
            val playResult = gameEngine.playBall(
                pitcher, currentBatter, defense, isRunnerOnFirst, state.outCount,
                totalPitchesNow
            )

            val scoreBefore = state.currentScore
            baseRunning.processPlay(state, playResult, currentBatter)

            print("[${state.outCount}사] ${batterIdx + 1}번 ${currentBatter.name}: ${playResult.detail}")

            if (state.scoreLog.isNotEmpty()) {
                state.scoreLog.forEach { log -> print("  ---> 👏 $log") }
            }
            println()

            val scoreMade = state.currentScore - scoreBefore
            inningScore += scoreMade

            if (targetScore != null && (currentTeamScore + inningScore) > targetScore) {
                println("🚀 끝내기 점수 발생! 경기 종료.")
                break
            }

            batterIdx = (batterIdx + 1) % 9
        }

        // 결과에 투구수(pitchesInInning)도 포함해서 리턴
        return HalfInningResult(inningScore, batterIdx, pitchesInInning)
    }

    // DTO 수정: pitchesThrown 추가
    data class HalfInningResult(val score: Int, val nextBatterIdx: Int, val pitchesThrown: Int)
}