# 1단계: 빌드 스테이지 (도커 안에서 직접 자바 17로 빌드를 수행합니다)
FROM gradle:8.5-jdk17 AS build
WORKDIR /app

# 현재 폴더의 모든 파일을 도커 컨테이너로 복사
COPY . .

# 실행 권한 부여 및 빌드 수행 (테스트는 건너뛰어 속도를 높입니다)
RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar -x test

# 2단계: 실행 스테이지 (빌드 도구는 버리고 실행에 필요한 엔진만 챙깁니다)
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

# 1단계에서 만들어진 따끈따끈한 .jar 파일만 쏙 가져옵니다
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]