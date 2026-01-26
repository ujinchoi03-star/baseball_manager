# Baseball Manager API Documentation

이 문서는 Baseball Manager 게임의 백엔드 API 명세를 설명합니다.

## Base Configuration
- **Server URL**: `http://localhost:8080`
- **WebSocket URL**: `ws://localhost:8080/ws/game`

---

## 1. Authentication (인증)

### 1-1. 구글 로그인
유저가 구글 로그인을 완료한 후 획득한 ID Token을 서버로 전송하여 인증합니다.

- **URL**: `/api/auth/login/google`
- **Method**: `POST`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "idToken": "GOOGLE_ID_TOKEN_STRING"
}
```

#### Response Body (Success)
```json
{
  "access_token": "SERVER_ISSUED_ACCESS_TOKEN", // (jwt 등)
  "user": {
    "id": 101,          // 서버 내부 유저 ID (Long)
    "nickname": "BaseballKing"
  }
}
```

---

## 2. Matchmaking (매칭 시스템)

### 2-1. 대기열 참가
게임을 시작하기 위해 매칭 대기열에 참가합니다.

- **URL**: `/api/matchmaking`
- **Method**: `POST`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "userId": 101 // 로그인 시 받은 유저 ID
}
```

#### Response Body
```json
{
  "status": "WAITING" // 또는 다른 상태 메시지
}
```

### 2-2. 매칭 상태 확인 (Polling)
대기열 참가 후, 1초 간격으로 호출하여 매칭 성사 여부를 확인합니다.

- **URL**: `/api/matchmaking/status`
- **Method**: `GET`
- **Query Parameters**:
  - `userId`: 유저 ID

#### Response Body (매칭 대기 중)
```json
{
  "status": "WAITING"
}
```

#### Response Body (매칭 성공)
```json
{
  "status": "MATCHED",
  "matchId": "ROOM_12345",  // 소켓 연결 시 사용
  "opponentId": 102
}
```
*(참고: 구체적인 JSON 필드는 MatchMakingService 구현에 따라 다를 수 있음)*

### 2-3. 대기 취소
매칭 대기를 취소합니다.

- **URL**: `/api/matchmaking`
- **Method**: `DELETE`
- **Query Parameters**:
  - `userId`: 유저 ID

#### Response Body
```json
{
  "status": "CANCELLED"
}
```

---

## 3. Team Management (팀 관리)

### 3-1. 보유 선수 목록 조회
유저가 보유한 모든 선수 카드를 조회합니다.

- **URL**: `/api/team/players`
- **Method**: `GET`

#### Response Body
```json
{
  "batters": [ ... ], // 타자 목록
  "pitchers": [ ... ] // 투수 목록
}
```

### 3-2. 라인업 저장
게임 시작 전(또는 팀 관리 화면에서) 설정한 라인업을 저장합니다.

- **URL**: `/api/team/lineup`
- **Method**: `POST`
- **Content-Type**: `application/json`

#### Request Body
```json
{
  "matchId": "ROOM_12345", // 매칭된 방 ID (게임 직전 저장 시)
  "activeLineup": {
    "starters": {
      "C": 101,   // 포수 ID
      "1B": 102,  // 1루수 ID
      "2B": 103,
      "3B": 104,
      "SS": 105,
      "LF": 106,
      "CF": 107,
      "RF": 108,
      "DH": 109
    },
    "battingOrder": [107, 105, 109, 102, 108, 106, 103, 101, 104], // 타순 (선수 ID 리스트)
    "bench": [110, 111, 112],
    "bullpen": [201, 202, 203] // 데려갈 투수 ID 리스트
  }
}
```

#### Response Body
```json
{
  "status": "SUCCESS",
  "message": "라인업이 DB에 저장되었습니다."
}
```

---

## 4. In-Game (WebSocket)

게임 플레이는 WebSocket을 통해 실시간으로 이루어집니다.

- **Connection URL**: `ws://localhost:8080/ws/game`

### 4-1. 메시지 포맷 (Protocol)
클라이언트와 서버는 아래 JSON 포맷으로 통신합니다.

```json
{
  "type": "ACTION_TYPE",   // 메시지 종류 (예: PITCH, HIT, CHAT 등)
  "matchId": "ROOM_ID",    // 매칭된 방 ID
  "senderId": 101,         // 보낸 유저 ID
  "content": "STRING_DATA" // 구체적인 데이터 (일반 문자열 또는 JSON 문자열)
}
```

### 4-2. 주요 Action Type 예시

#### 투수 투구 (Pitching)
```json
{
  "type": "PITCH",
  "matchId": "ROOM_123",
  "senderId": 101,
  "content": "FASTBALL" // 또는 구질/위치 정보 JSON
}
```

#### 타자 타격 (Hitting)
```json
{
  "type": "HIT",
  "matchId": "ROOM_123",
  "senderId": 102,
  "content": "SWING" // 또는 타이밍/위치 정보
}
```

#### 채팅 (Chat)
```json
{
  "type": "CHAT",
  "matchId": "ROOM_123",
  "senderId": 101,
  "content": "안녕하세요!"
}
```

### 4-3. 게임 진행 흐름
1. **Connect**: 프론트엔드에서 소켓 연결 (`ws://...`)
2. **Send**: 유저 행동 시 `send(JSON.stringify(message))` 
3. **Receive**: `onmessage` 이벤트를 통해 서버의 처리 결과 수신 및 UI 업데이트
