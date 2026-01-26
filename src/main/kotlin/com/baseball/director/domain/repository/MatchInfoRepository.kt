package com.baseball.director.domain.repository

import com.baseball.director.domain.entity.MatchInfo
import org.springframework.data.jpa.repository.JpaRepository

interface MatchInfoRepository : JpaRepository<MatchInfo, String> {
    // 기본 기능만 있으면 충분!
}