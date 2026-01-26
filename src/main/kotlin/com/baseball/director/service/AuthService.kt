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

        // ⭐ idToken을 이메일로 활용 (각각 다른 유저 생성)
        val email = "${idTokenString}@test.com"  // 수정!
        val name = "유저_${idTokenString.take(10)}"  // 수정!
        val picture = "https://example.com/pic.jpg"
        val sub = "google_${idTokenString}"  // 수정!

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