package com.baseball.director.controller

import com.baseball.director.domain.entity.Room
import com.baseball.director.domain.entity.RoomStatus
import com.baseball.director.domain.entity.MatchInfo
import com.baseball.director.domain.repository.RoomRepository
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.service.MatchMakingService
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/rooms")
class RoomController(
    private val roomRepository: RoomRepository,
    private val matchInfoRepository: MatchInfoRepository,
    private val matchMakingService: MatchMakingService  // ⭐ 추가
) {

    // 1. 방 생성 (친구초대) - POST /api/rooms
    @PostMapping
    fun createRoom(@RequestBody request: CreateRoomRequest): Map<String, Any> {
        // 친구 초대 방 생성
        val response = matchMakingService.createFriendRoom(request.user_id)

        return mapOf(
            "match_id" to response.matchId,
            "invite_code" to response.inviteCode,  // ⭐ 초대 코드 반환
            "status" to "WAITING"
        )
    }

    // 2. 방 참가 (코드입력) - POST /api/rooms/join
    @PostMapping("/join")
    fun joinRoom(@RequestBody request: JoinRoomRequest): Map<String, Any> {
        try {
            val response = matchMakingService.joinWithInviteCode(
                userId = request.guest_id,
                inviteCode = request.invite_code
            )

            return mapOf(
                "match_id" to response.matchId,
                "host_id" to response.hostId,
                "guest_id" to response.guestId,
                "status" to "PLAYING"
            )
        } catch (e: IllegalArgumentException) {
            return mapOf(
                "error" to "INVALID_CODE",
                "message" to (e.message ?: "유효하지 않은 초대 코드입니다")
            )
        } catch (e: IllegalStateException) {
            return mapOf(
                "error" to "ROOM_NOT_AVAILABLE",
                "message" to (e.message ?: "이미 게임이 시작된 방입니다")
            )
        }
    }

    // 3. 방 상태 조회 - GET /api/rooms/{matchId}
    @GetMapping("/{matchId}")
    fun getRoomStatus(@PathVariable matchId: String): Map<String, Any> {
        val room = roomRepository.findById(matchId)
            .orElseThrow {
                IllegalArgumentException("존재하지 않는 방입니다: $matchId")
            }

        return mapOf(
            "match_id" to room.matchId,
            "host_id" to room.hostId,
            "guest_id" to (room.guestId ?: 0),  // null이면 0
            "status" to room.status.name,
            "invite_code" to (room.inviteCode ?: ""),  // ⭐ 초대 코드 포함
            "match_type" to (room.matchType ?: "RANDOM")  // ⭐ 매치 타입 포함
        )
    }

    // 4. 방 삭제 - DELETE /api/rooms/{matchId}
    @DeleteMapping("/{matchId}")
    fun deleteRoom(@PathVariable matchId: String): Map<String, String> {
        roomRepository.deleteById(matchId)
        println("🗑️ 방 삭제: $matchId")
        return mapOf("message" to "방이 삭제되었습니다")
    }
}

// 요청 DTO
data class CreateRoomRequest(
    val user_id: Long
)

data class JoinRoomRequest(
    val invite_code: String,  // ⭐ 초대 코드
    val guest_id: Long
)