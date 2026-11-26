package server;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    // 모든 사용자 세션을 저장하는 공간
    private final ConcurrentHashMap<String, Session> sessions =
            new ConcurrentHashMap<>();

    // 새로운 세션 생성
    public Session createSession(String nickname, String roomId, String token) {

        String sessionId = UUID.randomUUID().toString();

        Session session = new Session(
                sessionId,
                nickname,
                roomId,
                token
        );

        sessions.put(sessionId, session);

        return session;
    }

    // 세션 ID로 세션 찾기
    public Session getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    // 토큰으로 세션 찾기
    public Session getSessionByToken(String token) {

        if (token == null) {
            return null;
        }

        for (Session session : sessions.values()) {
            if (token.equals(session.getToken())) {
                return session;
            }
        }

        return null;
    }

    // 세션 삭제
    public Session removeSession(String sessionId) {
        return sessions.remove(sessionId);
    }

    // 전체 세션 개수
    public int getSessionCount() {
        return sessions.size();
    }

    // 전체 세션 목록 반환
    public Collection<Session> getAllSessions() {
        return sessions.values();
    }
}