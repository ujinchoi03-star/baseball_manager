package com.baseball.director.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "match_record")
data class MatchRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "match_id", nullable = false)
    val matchId: String,

    @Column(nullable = false)
    val inning: Int,

    @Column(name = "event_type", nullable = false, length = 20)
    val eventType: String,  // PITCH, AT_BAT, MANAGEMENT

    @Column(name = "data", columnDefinition = "TEXT")
    val data: String,  // JSON

    @Column(name = "actor_id")
    val actorId: Long? = null,

    @Column(name = "description", columnDefinition = "TEXT")
    val description: String? = null
)