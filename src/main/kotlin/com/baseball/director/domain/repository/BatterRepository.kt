package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.Batter
import org.springframework.data.jpa.repository.JpaRepository

interface BatterRepository : JpaRepository<Batter, Long> {
    // 팀별 타자 목록 조회 (나중에 API에서 씀)
    fun findAllByTeam(team: String): List<Batter>
}