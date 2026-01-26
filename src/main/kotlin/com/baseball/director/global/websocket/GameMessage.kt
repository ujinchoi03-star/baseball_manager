package com.baseball.director.global.websocket

data class GameMessage(
    val matchId: String,      // ⭐ 순서 변경
    val senderId: Long,
    val type: String,
    val content: String? = null,  // ⭐ nullable로 변경
    val inning: Int? = null,      // ⭐ 추가
    val data: Map<String, Any>? = null  // ⭐ 추가
)

// ⭐ 새로 추가
data class GameResponse(
    val eventType: String,
    val matchId: String,
    val inning: Int,
    val description: String,
    val data: Map<String, Any>? = null,
    val timestamp: Long = System.currentTimeMillis()
)