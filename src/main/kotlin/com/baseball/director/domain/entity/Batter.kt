package com.baseball.director.domain.entity

import jakarta.persistence.*

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

    // --- 주루 ---
    val sb: Int,
    val cs: Int

) {
    // ⭐ [수정] cost -> credit 으로 변경
    @Column(nullable = false)
    var credit: Int = 5

    protected constructor() : this(
        null, "", "", 0, 0, 0, 0, 0, 0, 0, 0.0,
        0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0, 0,
        "", 0, 0.0, 0.0,
        0, 0
    )

    // ⭐ [로직] 급여(Credit) 자동 산정
    fun calculateAndSetCredit() {
        // 1. 기본 점수 (OPS 0.500 기준)
        var score = ((this.ops - 0.500) * 40).toInt()

        // 2. 홈런 보너스
        score += (this.homeRun * 0.2).toInt()

        // 3. 도루 보너스
        score += (this.sb * 0.15).toInt()

        // 4. 포지션 보너스
        when (this.position) {
            "C", "포수", "SS", "유격수" -> score += 2
            "2B", "2루수", "CF", "중견수" -> score += 1
        }

        // 5. 범위 제한 (5 ~ 25)
        this.credit = score.coerceIn(5, 25)
    }
}