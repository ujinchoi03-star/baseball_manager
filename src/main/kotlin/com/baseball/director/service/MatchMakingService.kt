package com.baseball.director.service

import com.baseball.director.domain.entity.*
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.MatchQueueRepository
import com.baseball.director.domain.repository.RoomRepository
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MatchMakingService(
    private val matchQueueRepository: MatchQueueRepository,
    private val roomRepository: RoomRepository,
    private val matchInfoRepository: MatchInfoRepository,
    private val messagingTemplate: SimpMessagingTemplate
) {

    // 1. 랜덤 매칭 신청
    @Transactional
    fun joinQueue(userId: Long, rating: Int): String {
        val existingRoom = roomRepository.findActiveRoom(userId, RoomStatus.PLAYING)
        if (existingRoom != null) {
            return "ALREADY_MATCHED"
        }

        matchQueueRepository.deleteById(userId)
        matchQueueRepository.save(MatchQueue(userId = userId, rating = rating))

        tryMatch(userId)

        return "QUEUED"
    }

    // 2. 매칭 시도 (랜덤)
    private fun tryMatch(myUserId: Long) {
        val allWaiting = matchQueueRepository.findAll()
            .filter { it.userId != myUserId }
            .sortedBy { it.joinedAt }

        val opponent = allWaiting.firstOrNull()

        if (opponent != null) {
            val matchId = UUID.randomUUID().toString().substring(0, 8).uppercase()

            val room = Room(
                matchId = matchId,
                hostId = opponent.userId,
                guestId = myUserId,
                status = RoomStatus.PLAYING,
                matchType = "RANDOM"
            )
            roomRepository.save(room)

            matchInfoRepository.save(MatchInfo(matchId = matchId))

            matchQueueRepository.deleteById(myUserId)
            matchQueueRepository.deleteById(opponent.userId)

            println("🎉 랜덤 매칭 성공! $matchId")
        }
    }

    // 3. 친구 초대 방 생성
    @Transactional
    fun createFriendRoom(userId: Long): FriendRoomResponse {
        val inviteCode = generateInviteCode()
        val matchId = UUID.randomUUID().toString().substring(0, 8).uppercase()

        val room = Room(
            matchId = matchId,
            hostId = userId,
            guestId = null,
            status = RoomStatus.WAITING,
            inviteCode = inviteCode,
            matchType = "FRIEND"
        )
        roomRepository.save(room)

        matchInfoRepository.save(MatchInfo(matchId = matchId))
        println("✅ 친구 초대 방 생성: $matchId, 코드: $inviteCode")

        return FriendRoomResponse(
            matchId = matchId,
            inviteCode = inviteCode
        )
    }

    // 4. 초대 코드로 입장
    @Transactional
    fun joinWithInviteCode(userId: Long, inviteCode: String): JoinRoomResponse {
        // 1. 방 찾기 - Kotlin 네이티브 방식
        val room = roomRepository.findByInviteCode(inviteCode)
            ?: throw IllegalArgumentException("유효하지 않은 초대 코드입니다")

        // 2. 이미 게스트가 있는지 확인
        if (room.guestId != null) {
            throw IllegalStateException("이미 게임이 시작된 방입니다")
        }

        // 3. 게스트 설정 및 상태 변경
        room.guestId = userId
        room.status = RoomStatus.PLAYING
        roomRepository.save(room)

        // 4. MatchInfo 조회 및 라인업 초기화
        val matchInfo = matchInfoRepository.findById(room.matchId).orElse(null)
            ?: throw IllegalArgumentException("MatchInfo를 찾을 수 없습니다")

        if (matchInfo.awayLineup.starters.isEmpty()) {
            matchInfo.awayLineup = Lineup()
        }
        matchInfoRepository.save(matchInfo)

        println("✅ 게스트 참가 완료: ${room.matchId}, hostId=${room.hostId}, guestId=${room.guestId}")

        // 5. WebSocket으로 Host에게 알림 전송
        try {
            messagingTemplate.convertAndSend(
                "/topic/room/${room.matchId}",
                mapOf(
                    "type" to "GUEST_JOINED",
                    "match_id" to room.matchId,
                    "host_id" to room.hostId,
                    "guest_id" to room.guestId,
                    "status" to "PLAYING"
                )
            )
            println("📨 WebSocket 알림 전송: Host(${room.hostId})에게 게스트 참가 알림")
        } catch (e: Exception) {
            println("⚠️ WebSocket 알림 실패: ${e.message}")
        }

        return JoinRoomResponse(
            matchId = room.matchId,
            hostId = room.hostId,
            guestId = room.guestId!!
        )
    }

    // 초대 코드 생성
    private fun generateInviteCode(): String {
        val chars = ('A'..'Z') + ('0'..'9')
        var code: String
        do {
            code = (1..6).map { chars.random() }.joinToString("")
        } while (roomRepository.findByInviteCode(code) != null)
        return code
    }

    // 5. 내 상태 확인
    @Transactional(readOnly = true)
    fun checkStatus(userId: Long): Map<String, Any> {
        val activeRoom = roomRepository.findActiveRoom(userId, RoomStatus.PLAYING)
        if (activeRoom != null) {
            return mapOf("status" to "MATCHED", "matchId" to activeRoom.matchId)
        }

        val waitingRoom = roomRepository.findByHostIdAndStatus(userId, RoomStatus.WAITING)
        if (waitingRoom != null && waitingRoom.matchType == "FRIEND") {
            return mapOf(
                "status" to "WAITING_FRIEND",
                "matchId" to waitingRoom.matchId,
                "inviteCode" to (waitingRoom.inviteCode ?: "")
            )
        }

        if (matchQueueRepository.existsById(userId)) {
            return mapOf("status" to "SEARCHING")
        }

        return mapOf("status" to "NONE")
    }

    // 6. 매칭 취소
    @Transactional
    fun cancelQueue(userId: Long) {
        matchQueueRepository.deleteById(userId)

        val waitingRoom = roomRepository.findByHostIdAndStatus(userId, RoomStatus.WAITING)
        if (waitingRoom != null && waitingRoom.matchType == "FRIEND") {
            roomRepository.delete(waitingRoom)
            println("✅ 친구 초대 방 삭제: ${waitingRoom.matchId}")
        }
    }
}

data class FriendRoomResponse(
    val matchId: String,
    val inviteCode: String
)

data class JoinRoomResponse(
    val matchId: String,
    val hostId: Long,
    val guestId: Long
)