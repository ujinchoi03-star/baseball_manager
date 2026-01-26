package com.baseball.director.domain.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(unique = true, nullable = false)
    val email: String,

    @Column(name = "provider_id")
    val providerId: String, // Google Sub ID

    var nickname: String,

    @Column(name = "profile_image")
    var profileImage: String? = null,

    @Enumerated(EnumType.STRING)
    val role: Role = Role.USER,

    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "last_login")
    var lastLogin: LocalDateTime = LocalDateTime.now()
)

enum class Role { USER, ADMIN }