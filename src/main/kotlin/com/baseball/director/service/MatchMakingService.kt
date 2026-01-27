package com.baseball.director.service

import com.baseball.director.domain.entity.*
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
        // 이미 진행 중인 게임이 있는지 확인 (Host인 경우)
        val existingRoom = roomRepository.findByHostIdAndStatus(userId, RoomStatus.PLAYING)
        if (existingRoom != null) {
            return "ALREADY_MATCHED"
        }

        // (Guest인 경우도 체크해주면 더 완벽함 - 생략 가능)

        // 기존 대기열 제거 후 재등록
        matchQueueRepository.deleteById(userId)
        matchQueueRepository.save(MatchQueue(userId = userId, rating = rating))

        // ⭐ 즉시 매칭 시도!
        tryMatch(userId)

        return "QUEUED"
    }

    // 2. 매칭 시도 로직
    private fun tryMatch(myUserId: Long) {
        // 나 말고 기다리는 사람 찾기 (가장 오래 기다린 사람)
        val allWaiting = matchQueueRepository.findAll()
            .filter { it.userId != myUserId }
            .sortedBy { it.joinedAt }

        val opponent = allWaiting.firstOrNull()

        if (opponent != null) {
            // 🎉 매칭 성사!
            val matchId = UUID.randomUUID().toString().substring(0, 8).uppercase()

            // ⭐ [중요 수정] Host와 Guest를 둘 다 명시해야 함!
            val room = Room(
                matchId = matchId,
                hostId = opponent.userId, // 먼저 기다린 사람이 방장
                guestId = myUserId,       // 내가 게스트
                status = RoomStatus.PLAYING // 바로 게임 시작 상태
            )
            roomRepository.save(room)

            // 게임 정보 초기화
            matchInfoRepository.save(MatchInfo(matchId = matchId))

            // 두 명 다 대기열에서 제거
            matchQueueRepository.deleteById(myUserId)
            matchQueueRepository.deleteById(opponent.userId)

            println("🎉 매칭 성공! 방 ID: $matchId (Host: ${opponent.userId} vs Guest: $myUserId)")
        }
    }

    // 3. 내 상태 확인 (폴링용)
    @Transactional(readOnly = true)
    fun checkStatus(userId: Long): Map<String, Any> {
        // 1) 내가 방장(Host)인 게임이 있나?
        val myRoomAsHost = roomRepository.findByHostIdAndStatus(userId, RoomStatus.PLAYING)
        if (myRoomAsHost != null) {
            return mapOf("status" to "MATCHED", "matchId" to myRoomAsHost.matchId)
        }

        // ⭐ 2) 내가 게스트(Guest)인 게임이 있나? (이 로직이 빠져 있었음)
        // (RoomRepository에 findByGuestIdAndStatus가 없으면 findAll로 필터링)
        val myRoomAsGuest = roomRepository.findAll().find {
            it.guestId == userId && it.status == RoomStatus.PLAYING
        }

        if (myRoomAsGuest != null) {
            return mapOf("status" to "MATCHED", "matchId" to myRoomAsGuest.matchId)
        }

        // 3) 아직 대기열에 있나?
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