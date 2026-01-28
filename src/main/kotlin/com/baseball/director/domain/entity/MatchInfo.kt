package com.baseball.director.domain.entity

import jakarta.persistence.*

@Entity
@Table(name = "match_info")
class MatchInfo(
    @Id
    @Column(name = "match_id")
    val matchId: String,

    var currentBatterIndex: Int? = 0,

    var inning: Int = 1,

    @Column(name = "is_top")
    var isTop: Boolean = true,

    @Column(name = "home_lineup_confirmed")
    var homeLineupConfirmed: Boolean = false,

    @Column(name = "away_lineup_confirmed")
    var awayLineupConfirmed: Boolean = false,

    @Column(name = "stadium")
    var stadium: String? = null,  // 구장 이름

    @Column(name = "home_team_id")
    var homeTeamId: Long? = null,  // 홈팀 userId

    @Column(name = "away_team_id")
    var awayTeamId: Long? = null,  // 원정팀 userId


    // ⭐ 이 필드가 있나요?
    @Column(name = "status")
    var status: String = "READY",  // READY, PLAYING, FINISHED

    // --- JSON 데이터들 ---

    @Convert(converter = ScoreConverter::class)
    @Column(name = "score", columnDefinition = "TEXT")
    var score: Score = Score(),

    @Convert(converter = RunnersConverter::class)
    @Column(name = "runners", columnDefinition = "TEXT")
    var runners: Runners = Runners(),

    // ⭐ ballCount는 딱 한 번만!
    @Convert(converter = BallCountConverter::class)
    @Column(name = "ball_count", columnDefinition = "TEXT")
    var ballCount: BallCount = BallCount(),

    // --- 라인업 정보 ---

    @Convert(converter = LineupConverter::class)
    @Column(name = "home_lineup", columnDefinition = "TEXT")
    var homeLineup: Lineup = Lineup(),

    @Convert(converter = LineupConverter::class)
    @Column(name = "away_lineup", columnDefinition = "TEXT")
    var awayLineup: Lineup = Lineup()
)