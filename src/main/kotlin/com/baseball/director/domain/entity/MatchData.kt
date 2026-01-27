package com.baseball.director.domain.entity

// 점수판
data class Score(
    var home: Int = 0,
    var away: Int = 0
)

// 볼카운트
data class BallCount(
    var b: Int = 0,
    var s: Int = 0,
    var o: Int = 0
)

// 주자 정보
data class Runners(
    val runnerIds: MutableList<Long?> = mutableListOf(null, null, null)
)

// ⭐ [중요] 이 클래스가 없어서 빨간 줄이 뜬 겁니다!
data class Lineup(
    var starters: MutableMap<String, Long> = mutableMapOf(),
    var battingOrder: MutableList<Long> = mutableListOf(),
    var bench: MutableList<Long> = mutableListOf(),
    var bullpen: MutableList<Long> = mutableListOf(),
    var currentOrder: Int = 0,
    var hasDH: Boolean = true
)