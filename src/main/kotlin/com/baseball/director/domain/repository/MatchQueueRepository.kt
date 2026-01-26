package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.MatchQueue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface MatchQueueRepository : JpaRepository<MatchQueue, Long> {

    // ⭐ 커스텀 쿼리로 명확하게 작성
    @Query("SELECT m FROM MatchQueue m WHERE m.userId != :userId ORDER BY m.joinedAt ASC LIMIT 1")
    fun findFirstByUserIdNotOrderByJoinedAtAsc(userId: Long): MatchQueue?

    // 또는 더 간단하게
    @Query("SELECT m FROM MatchQueue m WHERE m.userId != :userId ORDER BY m.joinedAt ASC")
    fun findFirstOpponent(userId: Long): List<MatchQueue>
}