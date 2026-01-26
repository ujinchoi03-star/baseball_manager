package com.baseball.director.domain.entity

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

// 공통으로 쓸 Jackson Mapper
private val objectMapper = jacksonObjectMapper()

// 1. 점수 컨버터
@Converter
class ScoreConverter : AttributeConverter<Score, String> {
    override fun convertToDatabaseColumn(attribute: Score): String {
        return objectMapper.writeValueAsString(attribute)
    }

    override fun convertToEntityAttribute(dbData: String?): Score {
        return if (dbData.isNullOrEmpty()) Score()
        else objectMapper.readValue(dbData)
    }
}

// 2. 볼카운트 컨버터
@Converter
class BallCountConverter : AttributeConverter<BallCount, String> {
    override fun convertToDatabaseColumn(attribute: BallCount): String {
        return objectMapper.writeValueAsString(attribute)
    }

    override fun convertToEntityAttribute(dbData: String?): BallCount {
        return if (dbData.isNullOrEmpty()) BallCount()
        else objectMapper.readValue(dbData)
    }
}

// 3. 주자 컨버터 (List<Long?> 처리)
@Converter
class RunnersConverter : AttributeConverter<Runners, String> {
    override fun convertToDatabaseColumn(attribute: Runners): String {
        return objectMapper.writeValueAsString(attribute.runnerIds)
    }

    override fun convertToEntityAttribute(dbData: String?): Runners {
        if (dbData.isNullOrEmpty()) return Runners()
        val list: MutableList<Long?> = objectMapper.readValue(dbData)
        return Runners(list)
    }
}

@Converter
class LineupConverter : AttributeConverter<Lineup, String> {
    private val objectMapper = jacksonObjectMapper()

    override fun convertToDatabaseColumn(attribute: Lineup): String {
        return objectMapper.writeValueAsString(attribute)
    }

    override fun convertToEntityAttribute(dbData: String?): Lineup {
        return if (dbData.isNullOrEmpty()) Lineup()
        else objectMapper.readValue(dbData)
    }
}