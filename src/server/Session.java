
package server;

import java.time.Duration;
import java.time.Instant;

public class Session {

    public enum Status {
        CONNECTED,
        DISCONNECTED,
        EXPIRED
    }

    private final String sessionId;
    private final String nickname;
    private final String roomId;
    private final String token;

    private Status status;
    private Instant disconnectedAt;

    public Session(String sessionId, String nickname,
                   String roomId, String token) {

        this.sessionId = sessionId;
        this.nickname = nickname;
        this.roomId = roomId;
        this.token = token;

        this.status = Status.CONNECTED;
        this.disconnectedAt = null;
    }

    // 연결이 끊겼을 때 호출
    public synchronized void disconnect() {
        if (status == Status.CONNECTED) {
            status = Status.DISCONNECTED;
            disconnectedAt = Instant.now();
        }
    }

    // 유예 시간이 지나지 않았을 때만 재접속 허용
    public synchronized boolean reconnect(long gracePeriodSeconds) {

        if (status != Status.DISCONNECTED || disconnectedAt == null) {
            return false;
        }

        long elapsedMillis = Duration.between(
                disconnectedAt,
                Instant.now()
        ).toMillis();

        // 유예 시간 60초가 지났으면 재접속 거부
        if (elapsedMillis >= gracePeriodSeconds * 1000) {
            return false;
        }

        status = Status.CONNECTED;
        disconnectedAt = null;

        return true;
    }

    // 유예 시간이 끝났을 때 세션 만료
    public synchronized void expire() {
        status = Status.EXPIRED;
    }

    // 세션 ID 반환
    public String getSessionId() {
        return sessionId;
    }

    // 닉네임 반환
    public String getNickname() {
        return nickname;
    }

    // 방 ID 반환
    public String getRoomId() {
        return roomId;
    }

    // 재접속 토큰 반환
    public String getToken() {
        return token;
    }

    // 현재 세션 상태 반환
    public synchronized Status getStatus() {
        return status;
    }

    // 연결이 끊긴 시각 반환
    public synchronized Instant getDisconnectedAt() {
        return disconnectedAt;
    }
}
