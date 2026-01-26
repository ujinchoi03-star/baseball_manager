package com.baseball.director.service

import com.baseball.director.domain.entity.User
import com.baseball.director.domain.repository.UserRepository
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.Collections

@Service
class AuthService(
    private val userRepository: UserRepository
) {
    // application.yml에 구글 클라이언트 ID 설정 필요 (없으면 테스트용 임시값 사용)
    @Value("\${google.client.id:YOUR_GOOGLE_CLIENT_ID}")
    lateinit var googleClientId: String

    @Transactional
    fun googleLogin(idTokenString: String): LoginResponse {
        // 1. 구글 ID 토큰 검증 (Google 라이브러리 사용)
        val verifier = GoogleIdTokenVerifier.Builder(NetHttpTransport(), GsonFactory())
            .setAudience(Collections.singletonList(googleClientId))
            .build()

        // ⚠️ 개발 단계에서는 검증 무시하고 싶다면 아래 주석을 풀고 가짜 데이터를 넣으세요.
        // val googleIdToken: GoogleIdToken? = null // 실제 검증 로직은 일단 스킵 (테스트 편의상)

        // [실제 로직]
        // val googleIdToken = verifier.verify(idTokenString)
        //     ?: throw IllegalArgumentException("유효하지 않은 구글 토큰입니다.")
        // val payload = googleIdToken.payload

        // [개발용 임시 로직] (프론트 없이 포스트맨 테스트용)
        val email = "test@gmail.com" // payload.email
        val name = "테스트유저" // payload.get("name") as String
        val picture = "https://example.com/pic.jpg" // payload.get("picture") as String
        val sub = "google_12345" // payload.subject

        // 2. DB 조회 (이미 가입된 유저인가?)
        val user = userRepository.findByEmail(email).orElseGet {
            // 3. 없으면 회원가입 (신규 저장)
            userRepository.save(User(
                email = email,
                providerId = sub,
                nickname = name,
                profileImage = picture
            ))
        }

        // 4. 우리 서버 전용 토큰 발급 (일단 심플하게 유저 ID 리턴)
        // 나중에 JWT로 업그레이드 할 예정
        return LoginResponse(
            accessToken = "TEMP_TOKEN_${user.id}",
            userId = user.id!!,
            nickname = user.nickname
        )
    }
}

// 응답 DTO
data class LoginResponse(
    val accessToken: String,
    val userId: Long,
    val nickname: String
)