package com.baseball.director.domain.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "match_queue")
class MatchQueue(
    @Id
    val userId: Long, // 유저 ID가 곧 PK (중복 줄서기 방지)

    val rating: Int = 1000, // 점수 (비슷한 실력끼리 매칭용)

    val joinedAt: LocalDateTime = LocalDateTime.now() // 기다린 시간
)