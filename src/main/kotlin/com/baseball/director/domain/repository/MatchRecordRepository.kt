package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.MatchRecord
import org.springframework.data.jpa.repository.JpaRepository

interface MatchRecordRepository : JpaRepository<MatchRecord, Long> {
    fun findByMatchIdOrderByIdAsc(matchId: String): List<MatchRecord>
    fun findByMatchIdAndEventType(matchId: String, eventType: String): List<MatchRecord>
}