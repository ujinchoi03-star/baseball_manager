package com.baseball.director.service

import com.baseball.director.domain.entity.MatchInfo
import com.baseball.director.domain.entity.MatchQueue
import com.baseball.director.domain.entity.Room
import com.baseball.director.domain.entity.RoomStatus
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.MatchQueueRepository
import com.baseball.director.domain.repository.RoomRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MatchMakingService(
    private val matchQueueRepository: MatchQueueRepository,
    private val roomRepository: RoomRepository,
    private val matchInfoRepository: MatchInfoRepository
) {

    // 1. 매칭 신청 (줄 서기)
    @Transactional
    fun joinQueue(userId: Long, rating: Int): String {

        val existingRoom = roomRepository.findByHostIdAndStatus(userId, RoomStatus.PLAYING)
        if (existingRoom != null) {
            return "ALREADY_MATCHED"
        }

        matchQueueRepository.deleteById(userId)

        // 대기열 등록
        matchQueueRepository.save(MatchQueue(userId = userId, rating = rating))

        // ⭐ 즉시 매칭 시도! (기다리는 사람이 있나?)
        tryMatch(userId)

        return "QUEUED"
    }

    // 2. 매칭 시도 로직
    private fun tryMatch(myUserId: Long) {
        // 나 말고 기다리는 사람 있나?
        val opponent = matchQueueRepository.findFirstByUserIdNotOrderByJoinedAtAsc(myUserId)

        if (opponent != null) {
            // 🎉 매칭 성사!
            val matchId = UUID.randomUUID().toString().substring(0, 8).uppercase() // 짧은 방 ID 생성

            // 방 생성 (DB 저장)
            val room = Room(matchId = matchId, hostId = opponent.userId, status = RoomStatus.PLAYING)
            roomRepository.save(room)

            // 게임 정보 초기화 (MatchInfo 생성)
            matchInfoRepository.save(MatchInfo(matchId = matchId))

            // 두 명 다 대기열에서 제거
            matchQueueRepository.deleteById(myUserId)
            matchQueueRepository.deleteById(opponent.userId)

            println("🎉 매칭 성공! 방 ID: $matchId (유저 ${opponent.userId} vs 유저 $myUserId)")
        }
    }

    // 3. 내 상태 확인 (폴링용)
    @Transactional(readOnly = true)
    fun checkStatus(userId: Long): Map<String, Any> {
        // 1) 내가 방장으로 된 게임이 있나? (매칭 성공)
        val myRoom = roomRepository.findByHostIdAndStatus(userId, RoomStatus.PLAYING)
        if (myRoom != null) {
            return mapOf("status" to "MATCHED", "matchId" to myRoom.matchId)
        }

        // 2) 아직 대기열에 있나?
        if (matchQueueRepository.existsById(userId)) {
            return mapOf("status" to "SEARCHING")
        }

        return mapOf("status" to "NONE")
    }

    // 4. 매칭 취소
    @Transactional
    fun cancelQueue(userId: Long) {
        matchQueueRepository.deleteById(userId)
    }
}