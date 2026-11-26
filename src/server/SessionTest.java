package server;

public class SessionTest {

    public static void main(String[] args) {

        SessionManager sessionManager = new SessionManager();
        TokenManager tokenManager = new TokenManager();

        ReconnectManager reconnectManager =
                new ReconnectManager(sessionManager, tokenManager);

        // 1. 토큰 발급
        String token = tokenManager.generateToken();

        System.out.println("[1] 토큰 발급: "
                + tokenManager.isValidToken(token));

        // 2. 세션 생성
        Session session = sessionManager.createSession(
                "Amy", "1", token
        );

        System.out.println("[2] 세션 생성: "
                + (session != null));

        // 3. 연결 종료
        session.disconnect();

        System.out.println("[3] 연결 종료: "
                + (session.getStatus() == Session.Status.DISCONNECTED));

        // 4. 정상 토큰으로 재접속
        Session restored = reconnectManager.reconnect(token);

        System.out.println("[4] 재접속 성공: "
                + (restored != null));

        System.out.println("[5] 연결 상태 복구: "
                + (session.getStatus() == Session.Status.CONNECTED));

        // 6. 잘못된 토큰으로 재접속
        Session invalid = reconnectManager.reconnect("wrong-token");

        System.out.println("[6] 잘못된 토큰 거부: "
                + (invalid == null));

        // 7. 세션 만료
        session.disconnect();
        session.expire();

        Session expired = reconnectManager.reconnect(token);

        System.out.println("[7] 만료 세션 재접속 거부: "
                + (expired == null));

        
// 8. 자동 만료 테스트
        System.out.println("\n[8] 자동 만료 테스트 시작");

        Session expirationSession = sessionManager.createSession(
        "Bob",
        "2",
        tokenManager.generateToken()
        );

        expirationSession.disconnect();

// 테스트에서는 3초로 설정
        SessionScheduler sessionScheduler =
            new SessionScheduler(sessionManager, 3);

        sessionScheduler.scheduleExpiration(expirationSession);

        try {
            Thread.sleep(4000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("[8] 세션 만료: "
                + (expirationSession.getStatus() == Session.Status.EXPIRED));

        System.out.println("[9] 세션 삭제: "
                + (sessionManager.getSession(
                        expirationSession.getSessionId()) == null));


        // 유예 시간이 지난 후 재접속이 거부되는지 테스트
        Session lateSession = sessionManager.createSession(
                "Late", "3", tokenManager.generateToken()
        );

        lateSession.disconnect();

        try {
        Thread.sleep(1100);
        } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        }

        System.out.println("[10] 유예 시간 초과 후 재접속 거부: "
        + (!lateSession.reconnect(1)));


        sessionScheduler.shutdown();

        System.out.println("\n테스트 종료!");
        }
}