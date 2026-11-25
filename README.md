# Network005_Team5_OrderTogether

5조 컴퓨터네트워크 텀프로젝트 "Order Together": Java TCP 소켓으로 만드는 콘솔 기반 실시간 공동 주문 서비스

## 준비물
- JDK 21 이상 (`java -version`, `javac -version`으로 확인)
  - 없으면 Oracle JDK 21 또는 Eclipse Temurin 21 설치 후 터미널을 다시 열기
- 외부 라이브러리, 빌드 도구(Maven, Gradle)는 쓰지 않음
- IDE(선택): VS Code는 Extension Pack for Java 설치, IntelliJ는 src 폴더를 Sources Root로 지정

## 빌드
저장소 최상위 폴더에서 실행. 컴파일 결과는 out 폴더에 생김

| OS | 명령 |
|---|---|
| Windows | `.\build.cmd` |
| macOS / Linux | `sh build.sh` |

- 코드를 받거나(pull) 고친 뒤에는 서버와 클라이언트를 모두 끄고 다시 빌드
- 아직 없는 시작 파일은 건너뛰고 있는 것만 빌드함

## 실행
저장소 최상위 폴더에서 실행

서버 (터미널 1개, 인자: 포트)
```
java -cp out server.ServerMain 5050
```

클라이언트 (사람 수만큼 터미널을 열어서, 인자: 서버 주소, 포트)
```
java -cp out client.ClientMain 127.0.0.1 5050
```

다른 PC에서 접속할 때는 127.0.0.1 대신 서버 PC의 IP 사용

## 시작 파일 (main이 있는 클래스)
| 클래스 | 파일 | 담당 |
|---|---|---|
| server.ServerMain | src/server/ServerMain.java | 1번 |
| client.ClientMain | src/client/ClientMain.java | 4번 |

- 빌드 스크립트는 위 파일과, 그 파일이 쓰는 클래스를 모두 컴파일함
- 시작 파일을 새로 만들면(예: 테스트 도구) build.cmd와 build.sh의 `ENTRIES`에 둘 다 추가
- Java 버전을 바꿀 때도 두 스크립트의 `RELEASE` 값을 함께 수정
