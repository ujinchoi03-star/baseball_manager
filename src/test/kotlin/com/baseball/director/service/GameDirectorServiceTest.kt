package com.baseball.director.service

import com.baseball.director.domain.entity.Batter
import com.baseball.director.domain.entity.Pitcher
import org.junit.jupiter.api.Test

class GameDirectorServiceTest {

    private val gameEngine = GameEngineService()
    private val baseRunning = BaseRunningService()
    private val director = GameDirectorService(gameEngine, baseRunning)

    @Test
    fun `풀게임_시뮬레이션_테스트_LG_vs_한화`() {
        // --- 1. 투수 생성 ---
        val homePitcher = createPitcher("류현진", "한화", ip = 150.0) // 체력 좋음
        val awayPitcher = createPitcher("임찬규", "LG", ip = 100.0) // 체력 보통

        // --- 2. 라인업 생성 (현실 스탯 반영) ---

        // [한화 이글스]
        val homeLineup = mutableListOf<Batter>()
        // 페라자: 호타준족 (HR 20, SB 10)
        homeLineup.add(createBatter("1번 페라자", "한화", 0.300, hr = 20, sb = 10, pos = "우익수"))
        // 문현빈: 교타자 (HR 5)
        homeLineup.add(createBatter("2번 문현빈", "한화", 0.280, hr = 5, sb = 5, pos = "2루수"))
        // 노시환: 홈런왕 (HR 31)
        homeLineup.add(createBatter("3번 노시환", "한화", 0.298, hr = 31, sb = 2, pos = "3루수"))
        // 채은성: 거포 (HR 23)
        homeLineup.add(createBatter("4번 채은성", "한화", 0.280, hr = 23, sb = 1, pos = "1루수"))
        // 안치홍: 중장거리 (HR 8)
        homeLineup.add(createBatter("5번 안치홍", "한화", 0.290, hr = 8, sb = 3, pos = "지명타자"))
        // 이진영: (HR 10)
        homeLineup.add(createBatter("6번 이진영", "한화", 0.250, hr = 10, sb = 5, pos = "중견수"))
        // 최재훈: 수비형 (HR 1)
        homeLineup.add(createBatter("7번 최재훈", "한화", 0.240, hr = 1, sb = 0, pos = "포수"))
        // 이도윤: (HR 1)
        homeLineup.add(createBatter("8번 이도윤", "한화", 0.260, hr = 1, sb = 10, pos = "유격수"))
        // 정은원: (HR 2)
        homeLineup.add(createBatter("9번 정은원", "한화", 0.250, hr = 2, sb = 5, pos = "좌익수"))


        // [LG 트윈스]
        val awayLineup = mutableListOf<Batter>()
        // 홍창기: 출루머신 (HR 1, SB 20) -> 홈런 거의 없음
        awayLineup.add(createBatter("1번 홍창기", "LG", 0.330, hr = 1, sb = 25, pos = "우익수"))
        // 박해민: 대도 (HR 3, SB 40)
        awayLineup.add(createBatter("2번 박해민", "LG", 0.290, hr = 3, sb = 40, pos = "중견수"))
        // 김현수: 타격기계 (HR 10)
        awayLineup.add(createBatter("3번 김현수", "LG", 0.300, hr = 10, sb = 3, pos = "좌익수"))
        // 오스틴: 거포 (HR 30)
        awayLineup.add(createBatter("4번 오스틴", "LG", 0.313, hr = 30, sb = 10, pos = "1루수"))
        // 박동원: 거포 포수 (HR 20)
        awayLineup.add(createBatter("5번 박동원", "LG", 0.270, hr = 20, sb = 0, pos = "포수"))
        // 문보경: 중장거리 (HR 10)
        awayLineup.add(createBatter("6번 문보경", "LG", 0.280, hr = 10, sb = 5, pos = "3루수"))
        // 오지환: 호타준족 (HR 8, SB 20)
        awayLineup.add(createBatter("7번 오지환", "LG", 0.270, hr = 8, sb = 20, pos = "유격수"))
        // 문성주: 교타자 (HR 2)
        awayLineup.add(createBatter("8번 문성주", "LG", 0.290, hr = 2, sb = 10, pos = "지명타자"))
        // 신민재: 육상부 (HR 0, SB 50) -> ⭐ 홈런 0개 설정!
        awayLineup.add(createBatter("9번 신민재", "LG", 0.285, hr = 0, sb = 50, pos = "2루수"))

        println("========== ⚾️ PLAY BALL! (데이터 현실 고증 패치) ⚾️ ==========")
        println("선발투수 예고: 류현진(한계 ${homePitcher.maxPitchCount}구) vs 임찬규(한계 ${awayPitcher.maxPitchCount}구)")

        val result = director.playGame(
            "한화 이글스", homePitcher, homeLineup,
            "LG 트윈스", awayPitcher, awayLineup
        )

        println("\n========== 🏁 GAME OVER 🏁 ==========")
        println("최종 스코어: [${result.winner} 승리]")
        println("원정 [${result.awayTeamName} ${result.awayScore}] : [${result.homeScore} ${result.homeTeamName}] 홈")

        println("\n--- 📋 전광판 (Scoreboard) ---")
        print("이닝 | ")
        for (i in 1..result.totalInning) print("$i  ")
        println("| R")
        print("원정 | ")
        result.scoreBoard.forEach { (_, score) -> print("${score.first}  ") }
        println("| ${result.awayScore}")
        print(" 홈  | ")
        result.scoreBoard.forEach { (_, score) ->
            val s = if(score.second == -1) "X" else score.second.toString()
            print("$s  ")
        }
        println("| ${result.homeScore}")
    }

    private fun createPitcher(name: String, team: String, ip: Double) = Pitcher(
        id = 1L, name = name, team = team, ip = ip,
        h = 100, hr = 10, bb = 30, hbp = 5, so = 100,
        go = 100, ao = 100, gdp = 10, error = 0, fpct = 0.95
    )

    // ⭐ hr 파라미터의 기본값을 5 -> 0으로 변경하여 실수 방지
    private fun createBatter(
        name: String, team: String, avg: Double,
        hr: Int = 0, // 기본값 0개!
        sb: Int = 0, triple: Int = 0,
        pos: String = "타자"
    ) = Batter(
        id = 1L, name = name, team = team, pa = 500,
        hit = (500 * avg).toInt(),
        doubleHit = 30, tripleHit = triple, homeRun = hr,
        sac = 0, sf = 0, avg = avg, strikeOut = 80, walk = 50, hbp = 5, gdp = 5,
        slg = 0.5, obp = 0.4, ops = 0.9, phBa = 0.0, go = 100, ao = 100,
        position = pos, error = 0, fpct = 0.98, csPct = 0.0,
        sb = sb, cs = 5
    )
}