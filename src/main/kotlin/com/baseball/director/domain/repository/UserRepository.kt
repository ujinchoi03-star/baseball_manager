package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    // 이메일로 유저 찾기 (로그인할 때 필수)
    fun findByEmail(email: String): Optional<User>
}