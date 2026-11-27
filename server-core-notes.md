# 1번 서버 코어·방 관리 공유 사항

- 기준: 2026-11-28 / 브랜치 `feat/room-management`
- 근거: 5조_Proposal.pdf 쪽 번호(p.), 미확정 사항은 docs/protocol-open-issues.md 번호
- 제안서대로 되돌림(11/28): 방장 LEAVE 시 방장 포함 전원 ROOM_END, 이름 앞뒤 공백 정리 안 함, 이름에 `|` → INVALID_NAME

---

## 1. 제안서와 다른 부분 (수정 이유, 네트워크 흐름 변화)

### 1) CREATE 뒤 STATUS
- 제안서: `OK|CREATE|번호|토큰`만 (p.7, p.9)
- 현재: OK → (MENU) → `STATUS|||0`
- 이유: 설계 공백. 방장도 주문 참가자(p.12), 현황은 서버만 계산(p.13~14). 입장 시 상태 전체 전송 → 이후 변경분만 방 전체 전송 방식을 방장에게도 적용
- 흐름 변화: S→방장 OK 뒤 MENU(2번 연결 후)·STATUS 추가. JOIN과 같은 순서

### 2) ERR|BAD_REQUEST (설계 공백으로 판단)
- 제안서: 에러 코드 10종에 없음 (p.11)
- 현재: 형식 오류 → `ERR|BAD_REQUEST`, 연결 유지
- 대상: 모르는 명령(`HELLO`), 필드 개수 오류(`JOIN|1`, `LEAVE|x`), 숫자 아닌 값(`JOIN|abc|Amy`)
- 필요성
  - 제안서 10종은 형식은 맞고 규칙만 어긋난 요청용임. 형식 오류용 코드 없음
  - 코드 없으면 무응답(클라이언트 대기, 무시 여부 모름) / 연결 끊기(오타 하나로 방·주문 상태 손실) / 엉뚱한 코드(`JOIN|1` → INVALID_NAME) 중 하나가 됨
  - 요청마다 OK 또는 ERR 응답, "Clear Error Handling"(p.14) 유지
  - 자동 테스트(5번)에서 형식 오류 확인 가능
- 반영: common ErrorCode(5번), 서버 요청 분배(1번)
- 흐름 변화: 형식 오류 요청 → S→C `ERR|BAD_REQUEST` (제안서: 응답 미정)

### 3) 필드 개수 검사
- 제안서: 정의 없음
- 현재: 모자람·남음 → BAD_REQUEST. 단 CREATE·JOIN 필드 남음 → INVALID_NAME (이름 속 `|`, 제안서 p.8)
- 이유: 설계 공백. 남는 필드를 무시하면 이름이 잘린 채 처리됨
- 흐름 변화: S→C `ERR|BAD_REQUEST` 또는 `ERR|INVALID_NAME`

### 4) 방 번호
- 제안서: "Rooms use room ID", 방 이름 중복 허용 (p.6, p.8)
- 현재: 1부터 증가, 겹치지 않음, 지운 번호 재사용 안 함 (`RoomManager.nextRoomId`, AtomicInteger)
- 이유: 겹치지 않음은 제안서 전제(번호가 유일한 구분자). 재사용 안 함은 추가 → 오래된 목록으로 엉뚱한 방 입장 방지, 재접속 NO_ROOM 판정(p.11). TCP TIME_WAIT와 같은 이유
- 흐름 변화: 없음 (ROOM_LIST 번호가 계속 증가)

### 5) RECONNECT의 ALREADY_IN_ROOM
- 제안서: ALREADY_IN_ROOM은 CREATE·JOIN만 (p.11)
- 현재: 방 안에서 RECONNECT → `ERR|ALREADY_IN_ROOM`
- 이유: 제안서 규칙 적용 (1 connection = 1 room, p.8). 한 연결이 두 방에 속하면 방 격리(p.14) 깨짐
- 흐름 변화: 방 안 RECONNECT → S→C `ERR|ALREADY_IN_ROOM`. 정상 재접속은 새 연결(로비에서 시작)이라 영향 없음

### 6) 연결 끊김 처리
- 제안서: 기본 단계 정의 없음 (확장만: 토큰 재접속 60초, PING/PONG, p.13)
- 현재: 끊기면 LEAVE와 같게 처리, 방장이면 방 전원 ROOM_END
- 이유: 설계 공백. 정리 안 하면 끊긴 사람이 정원 차지, 방장 끊기면 방이 계속 남음. 재접속 구현 때 3번과 변경
- 흐름 변화: TCP 종료(FIN·RST) 감지 → S→방 STATUS(2번 연결 후) 또는 ROOM_END → S→로비 ROOM_LIST

### 7) JOIN 검사 순서
- 제안서: "Check size·nick·closed" (p.9)
- 현재: 마감 → 정원 → 닉네임
- 비고: 기능적 문제는 없으나 완성도 측면에서 내용을 수정함, 확인 요망
- 이유: 바꿀 수 없는 이유(마감)를 먼저 알림 → 재시도 헛수고 방지
- 흐름 변화: 마감+꽉 참, 마감+닉네임 중복일 때만 `ERR|CLOSED` (제안서 순서: ROOM_FULL, DUP_NICK)

---

## 2. 보류된 사안 (적용X)
제안서에 기능상 문제가 있으나 중요하지 않다고 판단, 제안서대로 둠

| 항목 | 문제 | 보류 이유 |
|---|---|---|
| 이름 앞뒤 공백 정리 | " 철수"와 "철수"가 다른 이름 → 화면상 같은 닉네임 가능 | 4번 클라이언트가 공백 제거 후 전송 → 실사용 영향 없음 |
| 이름 길이 제한 | 긴 이름 → 콘솔 줄 밀림 | 프로토콜·네트워크 영향 없음 |
| 보이지 않는 문자 | 보이지 않는 문자가 붙은 이름을 다른 이름으로 처리 | 키보드 입력으로 거의 안 생김 |
| 방장 LEAVE 때 방장도 ROOM_END (p.9, p.12) | 방장 화면에 "방장이 나가서 방이 종료되었습니다" 추가 표시, 방 목록 재요청 | 표시 문제뿐임. 방 삭제·전원 로비 복귀 정상 |

---

## 3. 담당별 논의 사항

### 2번 (주문·마감)
- 방 락: Room 객체(`synchronized(room)`)를 주문 변경·마감에도 사용할지
- 연결 자리: `TeamHooks.orderCommand(연결, 방, 메시지)` → 부를 2번 메서드 이름
- NOT_HOST: `room.isHost(닉네임)` 제공. 검사 위치 (2번 메서드 안 제안)
- 메뉴판: 고정 메뉴 5~10개(p.13) 내용. MENU·STATUS 문자열 메서드 (`TeamHooks.menuLine`, `statusLine` 자리)
- STATUS 전송: `room.broadcast(줄)` 제공. 방 락 안 전송 여부
- 나간 사람 주문: `TeamHooks.onMemberLeft`(멤버 LEAVE·끊김), `onRoomEnded`(방장 퇴장) 호출됨. 마감 뒤 LEAVE 주문 유지 여부 (미확정 6번)
- 마감: CLOSE 때 `room.setClosed(true)` → 락 놓은 뒤 `rooms().pushRoomList()` (로비 CLOSED 표시, p.9). 마감된 방 JOIN은 1번이 `ERR|CLOSED`
- `OK|ORDER` 전송 여부 (미확정 2번)
- 원격에 2번 브랜치 없음 → 진행 상황 공유

### 3번 (연결·세션)
- 현재 구현 (참고)
  - 토큰: CREATE·JOIN 성공 → `TeamHooks.issueToken` → `TokenManager.generateToken()`
  - 끊김: `OrderServer.onDisconnect` → `RoomManager.onConnectionLost` → LEAVE와 같게 처리
  - RECONNECT·PONG: `TeamHooks.reconnect`, `pong` 자리만 있음 (응답 없음)
- 세션 생성(`SessionManager.createSession`) 시점: CREATE·JOIN 때인지
- 재접속 연결 교체: Session에 연결 없음 → Room 교체 메서드(1번) + 호출(3번)
- 끊김 처리 변경: 재접속 시 `onDisconnect`에서 3번 유예 처리 먼저. 유예 중 정원·목록 표시, 방장 끊김 (미확정 5번)
- RECONNECT 성공 응답: p.7 "restore" 내용 (OK → MENU → STATUS 제안). 방 없으면 `ERR|NO_ROOM` (p.11)
- PING: 사용 여부, 주기, 시간 초과 기준
- `Session.roomId` 타입 (String ↔ 방 번호 int)

### 4번 (클라이언트)
- `ERR|BAD_REQUEST` 안내 문구
- 방장 LEAVE 때 ROOM_END 수신 → 문구 중복 (보류 사안, 필요 시 처리)
- JOIN 뒤 MENU·STATUS는 2번 연결 후부터 옴
- 재접속: 토큰 저장 있음, RECONNECT 전송 없음

### 5번 (프로토콜·테스트)
- 형식 오류 기준: 모르는 명령·필드 개수·숫자 아닌 값 → BAD_REQUEST, CREATE·JOIN 필드 남음 → INVALID_NAME
- JOIN 검사 순서(마감 → 정원 → 닉네임) 기준 시험
- 이름 규칙: 제안서 그대로 (빈 이름, `| , :` 금지). 공백·길이는 보류
- 동시성 시험 결과 공유: 4명 방에 10명 동시 JOIN → 1명 성공 / 같은 닉네임 5명 동시 JOIN → 1명 성공

---

## 4. 보고서·발표 중요도
평가 기준: 최종 발표 self evaluation = Multi-thread 사용·동기화 처리 (O/X, X당 -10점), 시연영상 30점

| 중요도 | 내용 | 이유 |
|---|---|---|
| 상 | 멀티스레드: 접속 수락 스레드 + 연결마다 처리 스레드 | 평가 항목. 한 클라이언트가 전체를 막는 문제(강의 p.47) 해결 |
| 상 | 동기화: 방 락 안에서 입장 정원·닉네임 검사+추가, `send()` 동기화, 정리 1회, 락 겹침 방지 | 평가 항목 |
| 상 | 동시성 시험 결과 | 동기화 근거 |
| 상 | 프로토콜: 메시지 형식, `\n` 단위 구분·UTF-8, 요청·응답·방 단위 전송 | 네트워크 핵심. TCP는 메시지 경계 없음(강의 p.20) |
| 상 | 네트워크 구조: 중앙 서버, 로비 자동 갱신, 방 단위 전송 | 구조 표현, 시연 연결 |
| 상 | 시연: 여러 클라이언트 실시간 반영 | 시연영상 30점 |
| 중 | 연결 끊김 감지·처리 (FIN·RST 즉시, 신호 없는 끊김은 PING) | 네트워크 개념, 확장 기능 |
| 중 | 오류 처리 (에러 코드, BAD_REQUEST) | 프로토콜 완성도 |
| 중 | 방 번호 재사용 안 함 | 식별자 관리 (TCP TIME_WAIT 비유) |
| 중 | 스레드 이름 표시 서버 로그 | 멀티스레드 동작 증거 화면 |
| 중 | 작은 메시지 지연 방지 (`setTcpNoDelay`, Nagle) | 네트워크 개념, 짧게 언급 |
| 하 | 포트 인자, 포트 사용 중 안내, Ctrl+C 종료 처리 | 실행 편의 |
| 하 | 재접속 토큰 로그 가리기 | 보안 세부 |
| 하 | 이름 처리 규칙, 클라이언트 화면 문구 | 네트워크와 무관 |
| 하 | 빌드 스크립트, 실행 방법 | 환경 |
