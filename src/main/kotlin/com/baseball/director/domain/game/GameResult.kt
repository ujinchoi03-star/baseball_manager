package com.baseball.director.domain.game

data class GameResult(
    val homeTeamName: String,
    val awayTeamName: String,
    val homeScore: Int,
    val awayScore: Int,
    val winner: String, // 승리팀 이름 (무승부면 "Draw")
    val totalInning: Int, // 몇 회까지 했는지
    val scoreBoard: Map<Int, Pair<Int, Int>> // 이닝별 점수 (1회-> 1:0, 2회-> 0:2 ...)
)