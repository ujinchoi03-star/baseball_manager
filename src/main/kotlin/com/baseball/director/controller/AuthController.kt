package com.baseball.director.controller

import com.baseball.director.service.AuthService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/login/google")
    fun googleLogin(@RequestBody request: GoogleLoginRequest): Map<String, Any> {
        val response = authService.googleLogin(request.idToken)

        // API 명세서대로 응답 내려주기
        return mapOf(
            "access_token" to response.accessToken,
            "user" to mapOf(
                "id" to response.userId,
                "nickname" to response.nickname
            )
        )
    }
}

// 요청 DTO
data class GoogleLoginRequest(
    val idToken: String
)