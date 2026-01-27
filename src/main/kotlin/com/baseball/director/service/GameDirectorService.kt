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

        // Pitch count tracking
        var homePitcherPitches = 0
        var awayPitcherPitches = 0

        println("🏟️ 경기 시작: $awayTeamName (원정) vs $homeTeamName (홈)")
        println("--------------------------------------------------")

        for (inning in 1..12) {
            if (inning > 9 && homeScore != awayScore) break

            // --- [Top] Away Team Attack ---
            println("\n=== $inning 회 초 [$awayTeamName 공격] ===")
            val awayInningResult = playHalfInning(
                pitcher = homePitcher,
                currentPitchCount = homePitcherPitches,
                batters = awayLineup,
                defense = homeLineup,
                startBatterIdx = nextAwayBatterIdx,
                currentTeamScore = awayScore,
                targetScore = null
            )
            awayScore += awayInningResult.score
            nextAwayBatterIdx = awayInningResult.nextBatterIdx
            homePitcherPitches += awayInningResult.pitchesThrown

            // Check Pitcher Stamina (Home)
            val homeLimit = getPitcherLimit(homePitcher)
            if (homePitcherPitches > homeLimit) {
                println("⚠️ 홈팀 투수 체력 저하! (투구수: $homePitcherPitches / 한계: $homeLimit)")
            }

            // Mercy Rule / End Game Check
            if (inning >= 9 && homeScore > awayScore) {
                scoreBoard[inning] = Pair(awayInningResult.score, -1)
                println("\n=== $inning 회 말 생략 (홈팀 리드) ===")
                break
            }

            // --- [Bottom] Home Team Attack ---
            println("\n=== $inning 회 말 [$homeTeamName 공격] ===")
            val homeInningResult = playHalfInning(
                pitcher = awayPitcher,
                currentPitchCount = awayPitcherPitches,
                batters = homeLineup,
                defense = awayLineup,
                startBatterIdx = nextHomeBatterIdx,
                currentTeamScore = homeScore,
                targetScore = if (inning >= 9) awayScore else null
            )
            homeScore += homeInningResult.score
            nextHomeBatterIdx = homeInningResult.nextBatterIdx
            awayPitcherPitches += homeInningResult.pitchesThrown

            // Check Pitcher Stamina (Away)
            val awayLimit = getPitcherLimit(awayPitcher)
            if (awayPitcherPitches > awayLimit) {
                println("⚠️ 원정팀 투수 체력 저하! (투구수: $awayPitcherPitches / 한계: $awayLimit)")
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
        currentPitchCount: Int,
        batters: List<Batter>,
        defense: List<Batter>,
        startBatterIdx: Int,
        currentTeamScore: Int,
        targetScore: Int?
    ): HalfInningResult {

        val state = InningState()
        var batterIdx = startBatterIdx
        var inningScore = 0
        var pitchesInInning = 0

        while (state.outCount < 3) {
            val currentBatter = batters[batterIdx]

            // 3~6 pitches per batter
            val pitchesForBatter = (3..6).random()
            pitchesInInning += pitchesForBatter
            val totalPitchesNow = currentPitchCount + pitchesInInning

            // ⭐ [Fix 1] Find Catcher from defense list
            // (Assuming there is a catcher, otherwise pick first player to avoid crash)
            val catcher = defense.find { it.position == "C" || it.position == "포수" } ?: defense.first()

            // ⭐ [Fix 2] Find Lead Runner
            val leadRunner = state.thirdBase ?: state.secondBase ?: state.firstBase

            // ⭐ [Fix 3] Call playBall with CORRECT arguments
            val playResult = gameEngine.playBall(
                pitcher = pitcher,
                batter = currentBatter,
                catcher = catcher,          // Added
                defensePlayers = defense,
                runner = leadRunner,        // Added
                outCount = state.outCount,
                currentPitchCount = totalPitchesNow,
                tactic = "NORMAL"           // Default tactic for auto-sim
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

        return HalfInningResult(inningScore, batterIdx, pitchesInInning)
    }

    // ⭐ Helper function to calculate Max Pitch Count (Resolves red line issue)
    private fun getPitcherLimit(pitcher: Pitcher): Int {
        val calculatedMax = 15 + (pitcher.ip * 0.55).toInt()
        return calculatedMax.coerceIn(40, 110)
    }

    data class HalfInningResult(val score: Int, val nextBatterIdx: Int, val pitchesThrown: Int)
}