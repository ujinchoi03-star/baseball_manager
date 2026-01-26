package com.baseball.director.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "pitcher")
class Pitcher(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(nullable = false)
    val name: String,   // 선수 이름

    @Column(nullable = false)
    val team: String,   // 소속 팀 (한글)

    // --- 투구 기본 (Basic1) ---
    val ip: Double,     // 이닝
    val h: Int,         // 피안타
    val hr: Int,        // 피홈런
    val bb: Int,        // 볼넷
    val hbp: Int,       // 사구
    val so: Int,        // 탈삼진

    // --- 투구 세부 (Detail1) ---
    val go: Int,        // 땅볼 유도
    val ao: Int,        // 뜬공 유도
    val gdp: Int,       // 병살타 유도

    // --- ⭐ [NEW] 수비 (Defense) ---
    val error: Int,     // 실책 (ERR_CN)
    val fpct: Double    // 수비율 (FPCT_RT)

) {
    protected constructor() : this(
        null, "", "", 0.0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0 // 초기값
    )
}