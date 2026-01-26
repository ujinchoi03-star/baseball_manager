package com.baseball.director.service

import com.baseball.director.domain.entity.Lineup
import com.baseball.director.domain.entity.MatchInfo
import com.baseball.director.domain.repository.BatterRepository
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.PitcherRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TeamService(
    private val batterRepository: BatterRepository,
    private val pitcherRepository: PitcherRepository,
    private val matchInfoRepository: MatchInfoRepository // ⭐ 추가됨!
) {
    @Transactional(readOnly = true)
    fun getAllPlayers(): Map<String, Any> {
        return mapOf(
            "batters" to batterRepository.findAll(),
            "pitchers" to pitcherRepository.findAll()
        )
    }

    // ⭐ [NEW] 진짜 저장 로직
    @Transactional
    fun saveLineup(matchId: String, lineup: Lineup) {
        // 1. 이미 있는 방인지 확인 (없으면 새로 만듦)
        val matchInfo = matchInfoRepository.findById(matchId)
            .orElseGet { MatchInfo(matchId = matchId) }

        // 2. 일단 '홈팀' 라인업에 저장 (테스트용)
        // 나중에는 유저 ID랑 비교해서 홈/어웨이 구분할 예정
        matchInfo.homeLineup = lineup

        // 3. DB에 저장!
        matchInfoRepository.save(matchInfo)
    }
}