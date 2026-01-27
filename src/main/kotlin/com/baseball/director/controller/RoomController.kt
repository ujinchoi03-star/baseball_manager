package com.baseball.director.controller

import com.baseball.director.domain.repository.RoomRepository
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.service.MatchMakingService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/rooms")
class RoomController(
    private val roomRepository: RoomRepository,
    private val matchInfoRepository: MatchInfoRepository,
    private val matchMakingService: MatchMakingService
) {

    // 1. 방 생성 (친구초대) - POST /api/rooms
    @PostMapping
    fun createRoom(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            val userId = when (val id = request["user_id"]) {
                is Number -> id.toLong()
                is String -> id.toLong()
                else -> return ResponseEntity.badRequest().body(mapOf("error" to "Invalid user_id"))
            }

            val response = matchMakingService.createFriendRoom(userId)

            ResponseEntity.ok(mapOf(
                "match_id" to response.matchId,
                "invite_code" to response.inviteCode,
                "status" to "WAITING"
            ))
        } catch (e: Exception) {
            println("❌ 방 생성 실패: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to (e.message ?: "방 생성 실패")))
        }
    }

    // 2. 방 참가 (코드입력) - POST /api/rooms/join
    @PostMapping("/join")
    fun joinRoom(@RequestBody request: Map<String, Any>): ResponseEntity<Map<String, Any>> {
        return try {
            println("📨 받은 데이터: $request")

            // 초대 코드 추출 (invite_code 또는 match_id 둘 다 허용!)
            val inviteCode = when {
                request.containsKey("invite_code") -> request["invite_code"] as? String
                request.containsKey("inviteCode") -> request["inviteCode"] as? String
                request.containsKey("match_id") -> request["match_id"] as? String  // ⭐ 추가!
                request.containsKey("matchId") -> request["matchId"] as? String    // ⭐ 추가!
                else -> null
            }

            if (inviteCode == null) {
                println("❌ 초대 코드가 없음. 받은 키들: ${request.keys}")
                return ResponseEntity.badRequest()
                    .body(mapOf("error" to "초대 코드가 필요합니다"))
            }

            // 사용자 ID 추출
            val guestId = when {
                request.containsKey("guest_id") -> {
                    when (val id = request["guest_id"]) {
                        is Number -> id.toLong()
                        is String -> id.toLong()
                        else -> null
                    }
                }
                request.containsKey("guestId") -> {
                    when (val id = request["guestId"]) {
                        is Number -> id.toLong()
                        is String -> id.toLong()
                        else -> null
                    }
                }
                request.containsKey("user_id") -> {
                    when (val id = request["user_id"]) {
                        is Number -> id.toLong()
                        is String -> id.toLong()
                        else -> null
                    }
                }
                request.containsKey("userId") -> {
                    when (val id = request["userId"]) {
                        is Number -> id.toLong()
                        is String -> id.toLong()
                        else -> null
                    }
                }
                else -> null
            }

            if (guestId == null) {
                println("❌ 사용자 ID가 없음. 받은 키들: ${request.keys}")
                return ResponseEntity.badRequest()
                    .body(mapOf("error" to "사용자 ID가 필요합니다"))
            }

            println("✅ 파싱 성공: inviteCode=$inviteCode, guestId=$guestId")

            val response = matchMakingService.joinWithInviteCode(
                userId = guestId,
                inviteCode = inviteCode
            )

            println("✅ 방 참가 성공!")

            ResponseEntity.ok(mapOf(
                "match_id" to response.matchId,
                "host_id" to response.hostId,
                "guest_id" to response.guestId,
                "status" to "PLAYING"
            ))

        } catch (e: IllegalArgumentException) {
            println("❌ 잘못된 요청: ${e.message}")
            ResponseEntity.badRequest()
                .body(mapOf("error" to (e.message ?: "잘못된 요청입니다")))
        } catch (e: IllegalStateException) {
            println("❌ 방 상태 오류: ${e.message}")
            ResponseEntity.status(HttpStatus.CONFLICT)
                .body(mapOf("error" to (e.message ?: "방을 찾을 수 없습니다")))
        } catch (e: Exception) {
            println("❌ 예상치 못한 에러: ${e.message}")
            e.printStackTrace()
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "서버 오류가 발생했습니다"))
        }
    }

    // 3. 방 상태 조회 - GET /api/rooms/{matchId}
    @GetMapping("/{matchId}")
    fun getRoomStatus(@PathVariable matchId: String): ResponseEntity<Map<String, Any>> {
        return try {
            val room = roomRepository.findById(matchId).orElse(null)
                ?: return ResponseEntity.notFound().build()

            ResponseEntity.ok(mapOf(
                "match_id" to room.matchId,
                "host_id" to room.hostId,
                "guest_id" to (room.guestId ?: 0),
                "status" to room.status.name,
                "invite_code" to (room.inviteCode ?: ""),
                "match_type" to (room.matchType ?: "RANDOM")
            ))
        } catch (e: Exception) {
            println("❌ 방 조회 실패: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "방 조회 실패"))
        }
    }

    // 4. 방 삭제 - DELETE /api/rooms/{matchId}
    @DeleteMapping("/{matchId}")
    fun deleteRoom(@PathVariable matchId: String): ResponseEntity<Map<String, String>> {
        return try {
            roomRepository.deleteById(matchId)
            println("🗑️ 방 삭제: $matchId")
            ResponseEntity.ok(mapOf("message" to "방이 삭제되었습니다"))
        } catch (e: Exception) {
            println("❌ 방 삭제 실패: ${e.message}")
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(mapOf("error" to "방 삭제 실패"))
        }
    }
}