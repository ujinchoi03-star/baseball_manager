package com.baseball.director.domain.game

enum class PlayType {
    STRIKEOUT, WALK, HIT_BY_PITCH, // 삼진, 볼넷, 사구
    HOMERUN, HIT, // 홈런, 안타
    OUT, ERROR, GDP,SACRIFICE,STEAL_SUCCESS,STEAL_FAIL
}

data class PlayResult(
    val type: PlayType,     // 결과 타입 (안타, 아웃 등)
    val detail: String,     // 상세 내용 (예: "좌익수 앞 1루타", "유격수 땅볼 아웃")
    val hitType: Int = 0,   // 1=1루타, 2=2루타, 3=3루타, 4=홈런 (0=없음)
    val fielderName: String? = null // 수비한 선수 이름
)