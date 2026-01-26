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

    @Enumerated(EnumType.STRING)
    var status: RoomStatus = RoomStatus.WAITING
)

enum class RoomStatus { WAITING, PLAYING, FINISHED }