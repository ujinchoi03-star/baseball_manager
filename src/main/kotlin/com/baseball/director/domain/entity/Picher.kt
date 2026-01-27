package com.baseball.director.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "pitcher")
class Pitcher(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,

    @Column(nullable = false)
    val team: String,

    // --- 투구 기본 ---
    val ip: Double,
    val h: Int,
    val hr: Int,
    val bb: Int,
    val hbp: Int,
    val so: Int,

    // --- 투구 세부 ---
    val go: Int,
    val ao: Int,
    val gdp: Int,

    // --- 수비 ---
    val error: Int,
    val fpct: Double

) {
    // ⭐ [수정] cost -> credit 으로 변경
    @Column(nullable = false)
    var credit: Int = 5

    protected constructor() : this(
        null, "", "", 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0
    )

    // ⭐ [로직] 급여(Credit) 자동 산정
    fun calculateAndSetCredit() {
        if (this.ip == 0.0) {
            this.credit = 5
            return
        }

        // 1. WHIP 계산
        val whip = (this.h + this.bb) / this.ip

        // 2. 기본 점수 (WHIP 역산)
        var score = (30 - (whip * 12)).toInt()

        // 3. 탈삼진 (K/9) 보너스
        val k9 = (this.so / this.ip) * 9
        score += (k9 * 0.3).toInt()

        // 4. 이닝 소화력 (선발 우대)
        if (this.ip >= 144.0) score += 4
        else if (this.ip >= 100.0) score += 2

        // 5. 불펜 에이스 보정
        if (this.ip in 40.0..80.0 && whip < 1.10) {
            score += 3
        }

        // 6. 범위 제한
        this.credit = score.coerceIn(5, 25)
    }
}