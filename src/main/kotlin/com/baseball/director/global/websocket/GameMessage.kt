package com.baseball.director.global.websocket

// 유저가 보낼 메시지 (예: {"type": "PITCH", "matchId": "ROOM_123", "content": "FASTBALL"})
data class GameMessage(
    val type: String,   // "PITCH"(투구), "HIT"(타격), "Chat"(채팅) 등
    val matchId: String,
    val senderId: Long, // 누가 보냈는지
    val content: String // 구질, 타격 타이밍 등 데이터
)