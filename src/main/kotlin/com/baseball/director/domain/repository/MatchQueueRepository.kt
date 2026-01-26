package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.MatchQueue
import org.springframework.data.jpa.repository.JpaRepository

interface MatchQueueRepository : JpaRepository<MatchQueue, Long> {
    // 나보다 먼저 온 사람 중, 가장 오래 기다린 사람 1명 찾기
    fun findFirstByUserIdNotOrderByJoinedAtAsc(myUserId: Long): MatchQueue?
}