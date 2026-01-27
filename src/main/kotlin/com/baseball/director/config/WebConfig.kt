package com.baseball.director.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.converter.HttpMessageConverter
import org.springframework.http.converter.StringHttpMessageConverter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.nio.charset.StandardCharsets

@Configuration
class WebConfig : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true)
    }

    // ⭐ UTF-8 설정
    override fun configureMessageConverters(converters: MutableList<HttpMessageConverter<*>>) {
        // String Converter
        val stringConverter = StringHttpMessageConverter(StandardCharsets.UTF_8)
        stringConverter.setWriteAcceptCharset(false)
        converters.add(stringConverter)

        // JSON Converter
        val jsonConverter = MappingJackson2HttpMessageConverter()
        jsonConverter.defaultCharset = StandardCharsets.UTF_8
        jsonConverter.objectMapper = jacksonObjectMapper()
        converters.add(jsonConverter)
    }

    // ⭐ ObjectMapper Bean 설정
    @Bean
    fun objectMapper(): ObjectMapper {
        return jacksonObjectMapper()
    }
}