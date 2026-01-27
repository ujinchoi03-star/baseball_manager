package com.baseball.director.service

import com.baseball.director.domain.entity.User
import com.baseball.director.domain.repository.UserRepository
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository
) {
    @Value("\${spring.security.oauth2.client.registration.google.client-id}")
    lateinit var googleClientId: String

    @Value("\${jwt.secret}")
    lateinit var jwtSecret: String

    @Value("\${jwt.expiration}")
    var jwtExpiration: Long = 86400000 // 24시간

    private val verifier by lazy {
        GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory())
            .setAudience(Collections.singletonList(googleClientId))
            .build()
    }

    @Transactional
    fun googleLogin(idTokenString: String): LoginResponse {
        // 1. Google ID Token 검증
        val idToken: GoogleIdToken = verifier.verify(idTokenString)
            ?: throw IllegalArgumentException("Invalid ID token")

        val payload = idToken.payload
        val email = payload.email
        val name = payload["name"] as String
        val picture = payload["picture"] as String
        val sub = payload.subject

        // 2. DB 조회 (이미 가입된 유저인가?)
        val user = userRepository.findByEmail(email).orElseGet {
            // 3. 없으면 회원가입 (신규 저장)
            userRepository.save(User(
                email = email,
                providerId = "google_$sub",
                nickname = name,
                profileImage = picture
            ))
        }

        // 4. JWT 토큰 생성
        val token = generateJwtToken(user.id!!, user.email)

        return LoginResponse(
            accessToken = token,
            userId = user.id!!,
            nickname = user.nickname
        )
    }

    private fun generateJwtToken(userId: Long, email: String): String {
        val key = Keys.hmacShaKeyFor(jwtSecret.toByteArray())

        return Jwts.builder()
            .setSubject(email)
            .claim("userId", userId)
            .setIssuedAt(Date())
            .setExpiration(Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(key, SignatureAlgorithm.HS256)
            .compact()
    }
}

// 응답 DTO
data class LoginResponse(
    val accessToken: String,
    val userId: Long,
    val nickname: String
)