package com.baseball.director.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "room")
data class Room(
    @Id
    @Column(name = "match_id")
    val matchId: String,

    @Column(name = "host_id", nullable = false)
    val hostId: Long,

    @Column(name = "guest_id")
    var guestId: Long? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)  // ⭐ name 명시
    var status: RoomStatus = RoomStatus.WAITING,

    @Column(name = "invite_code", unique = true, length = 10)
    var inviteCode: String? = null,

    @Column(name = "match_type", length = 20)
    var matchType: String? = "RANDOM",  // ⭐ nullable로 변경 (기본값)

    @Column(name = "created_at", nullable = false, updatable = false)  // ⭐ updatable = false 추가
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class RoomStatus {
    WAITING,   // 대기 중
    PLAYING,   // 게임 중
    FINISHED   // 종료 (나중에 사용 예정)
}