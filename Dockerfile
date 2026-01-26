FROM openjdk:17-jdk-slim

WORKDIR /app

# Gradle 빌드 결과물 복사
COPY build/libs/*.jar app.jar

# 포트 노출
EXPOSE 8080

# 실행
ENTRYPOINT ["java", "-jar", "app.jar"]