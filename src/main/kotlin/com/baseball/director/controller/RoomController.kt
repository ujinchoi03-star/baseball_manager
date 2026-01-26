package com.baseball.director.controller

import com.baseball.director.domain.entity.Room
import com.baseball.director.domain.entity.RoomStatus
import com.baseball.director.domain.entity.MatchInfo
import com.baseball.director.domain.repository.RoomRepository
import com.baseball.director.domain.repository.MatchInfoRepository
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/rooms")
class RoomController(
    private val roomRepository: RoomRepository,
    private val matchInfoRepository: MatchInfoRepository
) {

    @PostMapping
    fun createRoom(@RequestBody request: CreateRoomRequest): Map<String, Any> {

        val existingRoom = roomRepository.findByHostIdAndStatus(request.user_id, RoomStatus.WAITING)
        if (existingRoom != null) {
            return mapOf(
                "match_id" to existingRoom.matchId,
                "status" to "WAITING",
                "message" to "이미 대기 중인 방이 있습니다"
            )
        }

        val matchId = UUID.randomUUID().toString()
            .replace("-", "")
            .take(6)
            .uppercase()

        val room = Room(
            matchId = matchId,
            hostId = request.user_id,
            status = RoomStatus.WAITING
        )
        roomRepository.save(room)

        matchInfoRepository.save(MatchInfo(matchId = matchId))

        println("🏠 방 생성: $matchId (방장: ${request.user_id})")

        return mapOf(
            "match_id" to matchId,
            "status" to "WAITING"
        )
    }

    @PostMapping("/join")
    fun joinRoom(@RequestBody request: JoinRoomRequest): Map<String, Any> {

        val room = roomRepository.findById(request.match_id)
            .orElseThrow {
                IllegalArgumentException("존재하지 않는 방입니다: ${request.match_id}")
            }

        if (room.status == RoomStatus.PLAYING) {
            throw IllegalStateException("이미 진행 중인 게임입니다")
        }

        if (room.hostId == request.guest_id) {
            throw IllegalStateException("자신이 만든 방에는 참가할 수 없습니다")
        }

        room.status = RoomStatus.PLAYING
        roomRepository.save(room)

        println("🚪 방 참가: ${request.match_id} (게스트: ${request.guest_id})")

        return mapOf(
            "match_id" to room.matchId,
            "status" to "PLAYING",
            "host_id" to room.hostId,
            "guest_id" to request.guest_id
        )
    }

    @GetMapping("/{matchId}")
    fun getRoomStatus(@PathVariable matchId: String): Map<String, Any> {
        val room = roomRepository.findById(matchId)
            .orElseThrow {
                IllegalArgumentException("존재하지 않는 방입니다: $matchId")
            }

        return mapOf(
            "match_id" to room.matchId,
            "host_id" to room.hostId,
            "status" to room.status.name
        )
    }

    @DeleteMapping("/{matchId}")
    fun deleteRoom(@PathVariable matchId: String): Map<String, String> {
        roomRepository.deleteById(matchId)
        println("🗑️ 방 삭제: $matchId")
        return mapOf("message" to "방이 삭제되었습니다")
    }
}

data class CreateRoomRequest(
    val user_id: Long
)

data class JoinRoomRequest(
    val match_id: String,
    val guest_id: Long
)