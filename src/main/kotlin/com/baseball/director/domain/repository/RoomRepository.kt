package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.Room
import com.baseball.director.domain.entity.RoomStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface RoomRepository : JpaRepository<Room, String> {

    fun findByHostIdAndStatus(hostId: Long, status: RoomStatus): Room?

    @Query("SELECT r FROM Room r WHERE r.status = :status AND (r.hostId = :userId OR r.guestId = :userId)")
    fun findActiveRoom(
        @Param("userId") userId: Long,
        @Param("status") status: RoomStatus
    ): Room?

    fun findByInviteCode(inviteCode: String): Room?
}