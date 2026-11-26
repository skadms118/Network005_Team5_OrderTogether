package server;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SessionScheduler {

    private static final long GRACE_PERIOD_SECONDS = 60;

    private final SessionManager sessionManager;
    private final long gracePeriodSeconds;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    // 실제 프로젝트용: 60초
    public SessionScheduler(SessionManager sessionManager) {
        this(sessionManager, GRACE_PERIOD_SECONDS);
    }

    // 테스트용: 원하는 시간 설정 가능
    public SessionScheduler(
            SessionManager sessionManager,
            long gracePeriodSeconds) {

        if (gracePeriodSeconds <= 0) {
            throw new IllegalArgumentException(
                    "유예 시간은 0보다 커야 합니다."
            );
        }

        this.sessionManager = sessionManager;
        this.gracePeriodSeconds = gracePeriodSeconds;
    }

    // 연결 종료 후 만료 검사 예약
    public void scheduleExpiration(Session session) {

        scheduleCheck(session, gracePeriodSeconds);
    }

    private void scheduleCheck(Session session, long delaySeconds) {

        scheduler.schedule(() -> {

            synchronized (session) {

                // 이미 재접속했거나 만료된 세션이면 종료
                if (session.getStatus() != Session.Status.DISCONNECTED) {
                    return;
                }

                Instant disconnectedAt = session.getDisconnectedAt();

                if (disconnectedAt == null) {
                    return;
                }

                long elapsedMillis = Duration.between(
                        disconnectedAt,
                        Instant.now()
                ).toMillis();

                long gracePeriodMillis =
                        gracePeriodSeconds * 1000;

                // 아직 유예 시간이 남았다면 다시 검사 예약
                if (elapsedMillis < gracePeriodMillis) {

                    long remainingMillis =
                            gracePeriodMillis - elapsedMillis;

                    long remainingSeconds =
                            (remainingMillis + 999) / 1000;

                    scheduleCheck(session, remainingSeconds);
                    return;
                }

                // 유예 시간 종료: 세션 만료 및 삭제
                session.expire();

                sessionManager.removeSession(
                        session.getSessionId()
                );

                System.out.println(
                        session.getNickname() + " 세션이 만료되었습니다."
                );
            }

        }, delaySeconds, TimeUnit.SECONDS);
    }

    // 스케줄러 종료
    public void shutdown() {
        scheduler.shutdown();
    }
}