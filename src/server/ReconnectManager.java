
package server;

public class ReconnectManager {

    private static final long GRACE_PERIOD_SECONDS = 60;

    private final SessionManager sessionManager;
    private final TokenManager tokenManager;

    public ReconnectManager(
            SessionManager sessionManager,
            TokenManager tokenManager) {

        this.sessionManager = sessionManager;
        this.tokenManager = tokenManager;
    }

    public Session reconnect(String token) {

        if (!tokenManager.isValidToken(token)) {
            return null;
        }

        Session session = sessionManager.getSessionByToken(token);

        if (session == null) {
            return null;
        }

        if (!tokenManager.matches(token, session.getToken())) {
            return null;
        }

        // 세션 내부에서 상태와 60초 경과 여부를 원자적으로 확인
        boolean success = session.reconnect(GRACE_PERIOD_SECONDS);

        if (!success) {
            return null;
        }

        return session;
    }
}
