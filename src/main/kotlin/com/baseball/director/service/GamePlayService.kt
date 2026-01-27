package com.baseball.director.service

import com.baseball.director.domain.entity.MatchInfo
import com.baseball.director.domain.game.InningState
import com.baseball.director.domain.repository.BatterRepository
import com.baseball.director.domain.repository.MatchInfoRepository
import com.baseball.director.domain.repository.MatchRecordRepository
import com.baseball.director.domain.repository.PitcherRepository
import com.baseball.director.domain.repository.RoomRepository
import com.baseball.director.global.websocket.GameMessage
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

@Service
class GamePlayService(
    private val matchInfoRepository: MatchInfoRepository,
    private val roomRepository: RoomRepository,
    private val batterRepository: BatterRepository,
    private val pitcherRepository: PitcherRepository,
    private val gameEngineService: GameEngineService,
    private val baseRunningService: BaseRunningService,
    private val matchRecordRepository: MatchRecordRepository
) {
    private val objectMapper = jacksonObjectMapper()
    /**
     * 웹소켓 요청을 분기 처리하는 메인 메서드
     */
    @Transactional
    fun handleAction(message: GameMessage): String {
        // 1. 경기 정보 가져오기
        val matchInfo = matchInfoRepository.findById(message.matchId)
            .orElseThrow { IllegalArgumentException("경기를 찾을 수 없습니다: ${message.matchId}") }

        // 2. 명령어 추출 (data.command가 우선, 없으면 type 사용)
        val data = message.data ?: emptyMap()
        val command = data["command"] as? String ?: message.type

        // 3. 명령(Command)에 따른 분기 처리
        return when (command) {
            // [A] 선수 교체 관련 (투수/야수, 대타, 대주자)
            "SUBSTITUTION" -> handlePlayerSubstitution(matchInfo, message, data)
            "PINCH_HITTER" -> handlePinchHitter(matchInfo, message, data)
            "PINCH_RUNNER" -> handlePinchRunner(matchInfo, message, data)

            // [B] 주루 작전 (적극적 주루 ON/OFF)
            "BASERUNNING" -> {
                val isAggressive = data["is_aggressive"] as? Boolean ?: false
                // ON이면 AGGRESSIVE_RUNNING, OFF면 NORMAL 모드
                val tactic = if (isAggressive) "AGGRESSIVE_RUNNING" else "NORMAL"
                handlePlayBall(matchInfo, message, tactic)
            }

            // [C] 기타 작전 (번트, 고의사구, 도루 등)
            // 프론트에서 { command: 'BUNT' } 등으로 보내면 여기서 처리
            "BUNT", "STEAL", "INTENTIONAL_WALK" -> handlePlayBall(matchInfo, message, command)

            // [D] 일반 진행
            "NORMAL" -> handlePlayBall(matchInfo, message, "NORMAL")

            else -> handlePlayBall(matchInfo, message, "NORMAL")
        }
    }

    // ==================================================================
    // 🔄 [교체 1] 일반 선수 교체 (SUBSTITUTION) - 주로 투수/수비수 교체
    // ==================================================================
    private fun handlePlayerSubstitution(matchInfo: MatchInfo, message: GameMessage, data: Map<String, Any>): String {
        val (targetLineup, teamName) = getTargetLineup(matchInfo, message)

        // ID 추출 (Number로 받고 Long으로 변환해야 안전함)
        val outPlayerId = (data["out_player_id"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("나가는 선수 정보가 없습니다.")
        val inPlayerId = (data["in_player_id"] as? Number)?.toLong()
            ?: throw IllegalArgumentException("들어오는 선수 정보가 없습니다.")

        // 1. 투수 교체인지 확인
        if (targetLineup.starters["P"] == outPlayerId) {
            val newPitcher = pitcherRepository.findById(inPlayerId)
                .orElseThrow { IllegalArgumentException("새 투수 정보가 없습니다.") }

            if (!targetLineup.bullpen.contains(inPlayerId)) throw IllegalArgumentException("불펜에 없는 선수입니다.")

            targetLineup.starters["P"] = inPlayerId
            targetLineup.bullpen.remove(inPlayerId)

            matchInfoRepository.save(matchInfo)
            return "🔄 [$teamName] 투수 교체! ${newPitcher.name} 등판."
        }
        // 2. 야수 교체인 경우
        else {
            val position = targetLineup.starters.entries.find { it.value == outPlayerId }?.key
                ?: throw IllegalArgumentException("선발 라인업에서 선수를 찾을 수 없습니다.")

            val newBatter = batterRepository.findById(inPlayerId).orElseThrow()
            if (!targetLineup.bench.contains(inPlayerId)) throw IllegalArgumentException("벤치에 없는 선수입니다.")

            // 수비 위치 변경
            targetLineup.starters[position] = inPlayerId
            // 타순 변경
            val orderIdx = targetLineup.battingOrder.indexOf(outPlayerId)
            if (orderIdx != -1) targetLineup.battingOrder[orderIdx] = inPlayerId

            targetLineup.bench.remove(inPlayerId)
            matchInfoRepository.save(matchInfo)
            return "🔄 [$teamName] 수비 교체! $position -> ${newBatter.name}"
        }
    }

    // ==================================================================
    // 🏏 [교체 2] 대타 (PINCH_HITTER)
    // ==================================================================
    private fun handlePinchHitter(matchInfo: MatchInfo, message: GameMessage, data: Map<String, Any>): String {
        val (targetLineup, teamName) = getTargetLineup(matchInfo, message)

        val outPlayerId = (data["out_player_id"] as? Number)?.toLong()!!
        val inPlayerId = (data["in_player_id"] as? Number)?.toLong()!!

        if (!targetLineup.bench.contains(inPlayerId)) throw IllegalArgumentException("벤치에 없는 선수입니다.")
        val newBatter = batterRepository.findById(inPlayerId).orElseThrow()

        // 1. 타순 변경
        val orderIdx = targetLineup.battingOrder.indexOf(outPlayerId)
        if (orderIdx == -1) throw IllegalArgumentException("타순에 없는 선수입니다.")
        targetLineup.battingOrder[orderIdx] = inPlayerId

        // 2. 수비 위치도 변경 (지명타자가 아니라면)
        val position = targetLineup.starters.entries.find { it.value == outPlayerId }?.key
        if (position != null) targetLineup.starters[position] = inPlayerId

        targetLineup.bench.remove(inPlayerId)
        matchInfoRepository.save(matchInfo)
        return "🔄 [$teamName] 대타 작전! ${newBatter.name} 타석에 들어섭니다."
    }

    // ==================================================================
    // 🏃 [교체 3] 대주자 (PINCH_RUNNER)
    // ==================================================================
    private fun handlePinchRunner(matchInfo: MatchInfo, message: GameMessage, data: Map<String, Any>): String {
        val (targetLineup, teamName) = getTargetLineup(matchInfo, message)

        val outPlayerId = (data["out_player_id"] as? Number)?.toLong()!!
        val inPlayerId = (data["in_player_id"] as? Number)?.toLong()!!

        if (!targetLineup.bench.contains(inPlayerId)) throw IllegalArgumentException("벤치에 없는 선수입니다.")
        val newRunner = batterRepository.findById(inPlayerId).orElseThrow()

        // 1. 주자 리스트 업데이트 (1,2,3루 중 어디에 있는지 찾아서 교체)
        val runnerIdx = matchInfo.runners.runnerIds.indexOf(outPlayerId)
        if (runnerIdx == -1) throw IllegalArgumentException("루상에 없는 주자입니다.")
        matchInfo.runners.runnerIds[runnerIdx] = inPlayerId

        // 2. 타순 및 수비 위치 변경 (대주자도 선수 교체임)
        val orderIdx = targetLineup.battingOrder.indexOf(outPlayerId)
        if (orderIdx != -1) targetLineup.battingOrder[orderIdx] = inPlayerId

        val position = targetLineup.starters.entries.find { it.value == outPlayerId }?.key
        if (position != null) targetLineup.starters[position] = inPlayerId

        targetLineup.bench.remove(inPlayerId)
        matchInfoRepository.save(matchInfo)
        return "🔄 [$teamName] 대주자 투입! ${newRunner.name} 뛸 준비를 합니다."
    }

    // [Helper] 팀 구분 로직
    private fun getTargetLineup(matchInfo: MatchInfo, message: GameMessage): Pair<com.baseball.director.domain.entity.Lineup, String> {
        val room = roomRepository.findById(message.matchId).orElseThrow()
        val isHome = (message.senderId == room.hostId)
        val isGuest = (message.senderId == room.guestId)
        if (!isHome && !isGuest) throw IllegalArgumentException("참가자가 아닙니다.")
        return if (isHome) matchInfo.homeLineup to "HOME" else matchInfo.awayLineup to "AWAY"
    }

    // ==================================================================
    // ⚾ [기능 2] 게임 진행 (타격 및 작전 수행)
    // ==================================================================
    private fun handlePlayBall(matchInfo: MatchInfo, message: GameMessage, tactic: String): String {
        // 1. 라인업 가져오기
        val isTop = matchInfo.isTop
        val attackLineup = if (isTop) matchInfo.awayLineup else matchInfo.homeLineup
        val defenseLineup = if (isTop) matchInfo.homeLineup else matchInfo.awayLineup

        // 2. 현재 타자 & 투수 찾기
        val currentBatterIdx = attackLineup.currentOrder
        val currentBatterId = attackLineup.battingOrder.getOrNull(currentBatterIdx)
            ?: throw IllegalStateException("타순 정보가 잘못되었습니다.")
        val currentPitcherId = defenseLineup.starters["P"]
            ?: throw IllegalStateException("상대 팀 투수가 없습니다.")

        val batter = batterRepository.findById(currentBatterId).orElseThrow()
        val pitcher = pitcherRepository.findById(currentPitcherId).orElseThrow()

        // 3. 수비수 정보 로딩
        val defenseIds = defenseLineup.starters.values.filter { it != currentPitcherId }.toList()
        val defensePlayers = batterRepository.findAllById(defenseIds)

        // 4. 이닝 상태 구성
        val state = InningState(
            outCount = matchInfo.ballCount.o,
            currentScore = if (isTop) matchInfo.score.away else matchInfo.score.home
        ).apply {
            firstBase = matchInfo.runners.runnerIds[0]?.let { batterRepository.findById(it).orElse(null) }
            secondBase = matchInfo.runners.runnerIds[1]?.let { batterRepository.findById(it).orElse(null) }
            thirdBase = matchInfo.runners.runnerIds[2]?.let { batterRepository.findById(it).orElse(null) }
        }

        // 5. 도루/작전을 위한 포수 & 주자 정보 로딩
        val catcherId = defenseLineup.starters["C"]!!
        val catcher = batterRepository.findById(catcherId).orElseThrow()
        val leadRunner = state.thirdBase ?: state.secondBase ?: state.firstBase

        // 6. 게임 엔진 실행
        val estimatedPitchCount = matchInfo.inning * 15
        val playResult = gameEngineService.playBall(
            pitcher, batter, catcher, defensePlayers, leadRunner, state.outCount, estimatedPitchCount, tactic
        )

        // 7. 주루 플레이 처리 (AGGRESSIVE_RUNNING 여부는 tactic으로 전달됨)
        val scoreBefore = state.currentScore  // ⭐ 추가
        baseRunningService.processPlay(state, playResult, batter, tactic)
        val scoreAfter = state.currentScore   // ⭐ 추가
        val scoreChange = scoreAfter - scoreBefore  // ⭐ 추가

        // ⭐ [추가] MATCH_RECORD에 저장
        saveMatchRecord(matchInfo, playResult, batter, scoreChange)


        // 8. 결과 반영
        matchInfo.ballCount.o = state.outCount
        matchInfo.runners.runnerIds[0] = state.firstBase?.id
        matchInfo.runners.runnerIds[1] = state.secondBase?.id
        matchInfo.runners.runnerIds[2] = state.thirdBase?.id

        if (isTop) matchInfo.score.away = state.currentScore
        else matchInfo.score.home = state.currentScore

        // 로그 메시지 생성
        val resultPrefix = if (tactic != "NORMAL") "[작전: $tactic] " else ""
        val subjectName = if (tactic == "STEAL" && leadRunner != null) leadRunner.name else batter.name
        var resultMessage = "⚾ $resultPrefix$subjectName: ${playResult.detail}"

        if (state.scoreLog.isNotEmpty()) {
            resultMessage += "\n👏 " + state.scoreLog.joinToString(", ")
        }

        // 9. 타순 변경 (도루 시도는 타석 유지)
        if (tactic != "STEAL") {
            attackLineup.currentOrder = (currentBatterIdx + 1) % 9
            matchInfo.currentBatterIndex = attackLineup.currentOrder
        }

        // 10. 3아웃 공수교대 체크
        if (matchInfo.ballCount.o >= 3) {
            changeInning(matchInfo)
            resultMessage += "\n🔄 3아웃 공수교대! (${matchInfo.inning}회 ${if (matchInfo.isTop) "초" else "말"})"
            val newLineup = if (matchInfo.isTop) matchInfo.awayLineup else matchInfo.homeLineup
            matchInfo.currentBatterIndex = newLineup.currentOrder
            checkGameEnd(matchInfo)
        }

        matchInfoRepository.save(matchInfo)
        return resultMessage
    }

    private fun changeInning(matchInfo: MatchInfo) {
        matchInfo.ballCount.o = 0
        matchInfo.ballCount.b = 0
        matchInfo.ballCount.s = 0
        matchInfo.runners.runnerIds.replaceAll { null }
        matchInfo.isTop = !matchInfo.isTop
        if (matchInfo.isTop) matchInfo.inning++
    }

    // ⭐ [새 메서드] MATCH_RECORD 저장
    private fun saveMatchRecord(
        matchInfo: MatchInfo,
        playResult: com.baseball.director.domain.game.PlayResult,
        batter: com.baseball.director.domain.entity.Batter,
        scoreChange: Int
    ) {
        val data = mapOf(
            "batter_id" to (batter.id ?: 0L),
            "result" to playResult.type.name,
            "detail" to playResult.detail,
            "hit_type" to playResult.hitType,
            "score_change" to scoreChange
        )

        val record = com.baseball.director.domain.entity.MatchRecord(
            matchId = matchInfo.matchId,
            inning = matchInfo.inning,
            eventType = "AT_BAT",
            data = objectMapper.writeValueAsString(data),
            actorId = batter.id,
            description = playResult.detail
        )

        matchRecordRepository.save(record)
        println("📝 MATCH_RECORD 저장: ${batter.name} - ${playResult.detail}")
    }

    // ⭐ [개선] 게임 종료 체크
    private fun checkGameEnd(matchInfo: MatchInfo): Boolean {
        // 9회말 종료 후 체크
        if (matchInfo.inning >= 9 && matchInfo.isTop) {
            val homeScore = matchInfo.score.home
            val awayScore = matchInfo.score.away

            // 동점이 아니면 게임 종료
            if (homeScore != awayScore) {
                matchInfo.status = "FINISHED"

                // Room 상태도 업데이트
                val room = roomRepository.findById(matchInfo.matchId).orElseThrow()
                room.status = com.baseball.director.domain.entity.RoomStatus.FINISHED
                roomRepository.save(room)

                println("🏁 게임 종료! 최종 스코어 - Home: $homeScore, Away: $awayScore")
                return true
            }
        }

        // 12회 종료 (최대 연장)
        if (matchInfo.inning > 12) {
            matchInfo.status = "FINISHED"
            val room = roomRepository.findById(matchInfo.matchId).orElseThrow()
            room.status = com.baseball.director.domain.entity.RoomStatus.FINISHED
            roomRepository.save(room)

            println("🏁 게임 종료! 12회 무승부")
            return true
        }

        return false
    }
}



