# KBO 야구 게임 백엔드

## 🚀 빠른 시작

### 1. 환경 설정 (1분 완료!)

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

### 2. MySQL 데이터베이스 생성
```sql
CREATE DATABASE baseball_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 실행
```bash
./gradlew bootRun
```

**끝!** 🎉

---

## 📡 주요 API

- **로그인:** `POST /api/auth/login/google`
- **매칭:** `POST /api/matchmaking`
- **선수 조회:** `GET /api/team/players`
- **라인업 저장:** `POST /api/team/lineup`
- **WebSocket:** `ws://localhost:8080/ws-baseball`

자세한 API 명세는 `api-test.http` 파일 참고

---

## 🔒 보안

- `application-local.yml`은 `.gitignore`에 포함되어 Git에 올라가지 않음
- 각자 본인의 MySQL 비밀번호를 로컬 파일에만 저장

---

## 📅 출시 예정

**2026년 1월 29일 (수) 저녁**