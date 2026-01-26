# KBO 야구 게임 백엔드

## 🚀 빠른 시작

### 방법 1: Docker 사용 (추천! 가장 쉬움 ⭐)

#### 1. 환경 설정
```bash
# 프로젝트 클론
git clone [repository-url]
cd director

# .env 파일 생성
copy .env.example .env  # Windows
cp .env.example .env    # Mac/Linux

# .env 파일 열어서 DB_PASSWORD 수정
```

#### 2. 실행
```bash
docker-compose up --build
```

**끝!** 🎉
- API: http://localhost:8080
- MySQL: localhost:3307 (user: baseball_user)

#### 3. 중지
```bash
docker-compose down
```

---

### 방법 2: 로컬 환경 사용

#### Windows
```powershell
# 1. 프로젝트 클론
git clone [repository-url]
cd director

# 2. 로컬 설정 파일 생성
copy src\main\resources\application-local.yml.example src\main\resources\application-local.yml

# 3. application-local.yml 파일 열어서 MySQL 비밀번호만 수정
```

#### Mac/Linux
```bash
# 1. 프로젝트 클론
git clone [repository-url]
cd director

# 2. 로컬 설정 파일 생성
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

# 3. application-local.yml 파일 열어서 MySQL 비밀번호만 수정
```

#### MySQL 데이터베이스 생성
```sql
CREATE DATABASE baseball_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

#### 실행
```bash
./gradlew bootRun
```

---

## 📡 주요 API

- **로그인:** `POST /api/auth/login/google`
- **매칭:** `POST /api/matchmaking`
- **선수 조회:** `GET /api/team/players`
- **라인업 저장:** `POST /api/team/lineup`
- **WebSocket:** `ws://localhost:8080/ws-baseball`

자세한 API 명세는 `api-test.http` 파일 참고

---

## 🐳 Docker 상세 정보

### 포트
- **Spring Boot:** 8080
- **MySQL:** 3307 (외부 접속용)

### MySQL 접속 정보
- **Host:** localhost
- **Port:** 3307
- **Database:** baseball_db
- **Username:** baseball_user
- **Password:** .env 파일에서 설정한 비밀번호

### 데이터
- **타자:** 145명
- **투수:** 138명
- 애플리케이션 시작 시 자동으로 데이터 삽입됨

---

## 🔒 보안

- `application-local.yml`은 `.gitignore`에 포함되어 Git에 올라가지 않음
- `.env` 파일도 `.gitignore`에 포함됨
- 각자 본인의 비밀번호를 로컬 파일에만 저장

---

## 📅 출시 예정

**2026년 1월 29일 (수) 저녁**

---

## 🛠 기술 스택

- **Backend:** Kotlin, Spring Boot 3.5.10
- **Database:** MySQL 8.0
- **Real-time:** WebSocket (STOMP)
- **Deployment:** Docker, Docker Compose
- **ORM:** JPA/Hibernate