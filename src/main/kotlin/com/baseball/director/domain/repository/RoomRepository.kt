package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.Room
import org.springframework.data.jpa.repository.JpaRepository

interface RoomRepository : JpaRepository<Room, String> {
    // 방장의 ID로, 현재 'WAITING' 상태인 방 찾기 (매칭 확인용)
    fun findByHostIdAndStatus(hostId: Long, status: com.baseball.director.domain.entity.RoomStatus): Room?

    @org.springframework.data.jpa.repository.Query("SELECT r FROM Room r WHERE r.status = :status AND (r.hostId = :userId OR r.guestId = :userId)")
    fun findActiveRoom(@org.springframework.data.repository.query.Param("userId") userId: Long, @org.springframework.data.repository.query.Param("status") status: com.baseball.director.domain.entity.RoomStatus): Room?

    // 초대받은 손님이 들어간 방 찾기 (나중에 필요)
    // fun findByGuestIdAndStatus(...)
}