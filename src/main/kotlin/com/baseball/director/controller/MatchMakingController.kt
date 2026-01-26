package com.baseball.director.controller

import com.baseball.director.service.MatchMakingService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/matchmaking")
class MatchMakingController(
    private val matchMakingService: MatchMakingService
) {

    // 매칭 신청
    @PostMapping
    fun joinQueue(@RequestBody request: Map<String, Long>): Map<String, String> {
        val userId = request["userId"]!!
        val status = matchMakingService.joinQueue(userId, 1000)
        return mapOf("status" to status)
    }

    // 상태 확인 (1초마다 호출될 예정)
    @GetMapping("/status")
    fun checkStatus(@RequestParam userId: Long): Map<String, Any> {
        return matchMakingService.checkStatus(userId)
    }

    // 매칭 취소
    @DeleteMapping
    fun cancelQueue(@RequestParam userId: Long): Map<String, String> {
        matchMakingService.cancelQueue(userId)
        return mapOf("status" to "CANCELLED")
    }
}