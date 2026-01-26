package com.baseball.director.domain.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "batter")
class Batter(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val team: String,

    // --- 타격 ---
    val pa: Int, val hit: Int, val doubleHit: Int, val tripleHit: Int, val homeRun: Int,
    val sac: Int, val sf: Int, val avg: Double,
    val strikeOut: Int, val walk: Int, val hbp: Int, val gdp: Int,
    val slg: Double, val obp: Double, val ops: Double, val phBa: Double,
    val go: Int, val ao: Int,

    // --- 수비 ---
    val position: String,
    val error: Int,
    val fpct: Double,
    val csPct: Double,

    // --- ⭐ [NEW] 주루 (Runner) ---
    val sb: Int,      // 도루 성공 (SB_CN)
    val cs: Int       // 도루 실패 (SBA_CN - SB_CN)

) {
    protected constructor() : this(
        null, "", "", 0, 0, 0, 0, 0, 0, 0, 0.0,
        0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0, 0,
        "", 0, 0.0, 0.0,
        0, 0 // 주루 초기값
    )
}