package com.baseball.director.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "room")
class Room(
    @Id
    @Column(name = "match_id")
    val matchId: String, // 방 ID (초대 코드 겸용)

    @Column(name = "host_id")
    val hostId: Long, // 방장 ID

    @Column(name = "guest_id")  // ⭐ 추가
    var guestId: Long? = null,  // ⭐ 추가 (nullable, 아직 참가 안 했을 수 있음)

    @Enumerated(EnumType.STRING)
    var status: RoomStatus = RoomStatus.WAITING
)

enum class RoomStatus { WAITING, PLAYING, FINISHED }