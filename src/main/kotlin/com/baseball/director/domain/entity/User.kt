package com.baseball.director.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(name = "provider_id", nullable = false)
    val providerId: String, // Google Sub ID

    @Column(nullable = false)
    var nickname: String,

    @Column(name = "profile_image")
    var profileImage: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.USER,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_login")
    var lastLogin: LocalDateTime = LocalDateTime.now()
) {
    // JPA를 위한 기본 생성자 (Kotlin에서는 자동 생성됨)
}

enum class Role {
    USER,
    ADMIN
}