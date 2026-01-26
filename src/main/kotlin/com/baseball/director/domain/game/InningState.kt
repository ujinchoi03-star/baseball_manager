package com.baseball.director.domain.game

import com.baseball.director.domain.entity.Batter

data class InningState(
    var firstBase: Batter? = null,  // 1루 주자
    var secondBase: Batter? = null, // 2루 주자
    var thirdBase: Batter? = null,  // 3루 주자
    var outCount: Int = 0,          // 아웃 카운트
    var currentScore: Int = 0,      // 현재 이닝 득점
    var scoreLog: MutableList<String> = mutableListOf() // "홍창기 득점" 같은 로그 저장
) {
    // 주자 싹 지우기 (공수교대 시)
    fun reset() {
        firstBase = null; secondBase = null; thirdBase = null
        outCount = 0; currentScore = 0; scoreLog.clear()
    }
}