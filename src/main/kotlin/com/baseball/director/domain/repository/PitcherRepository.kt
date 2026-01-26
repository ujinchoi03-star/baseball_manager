package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.Pitcher
import org.springframework.data.jpa.repository.JpaRepository

interface PitcherRepository : JpaRepository<Pitcher, Long> {
    fun findAllByTeam(team: String): List<Pitcher>
}