package server;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 서버 로그. 줄마다 시각과 스레드 이름을 붙인다.
 * 예: [14:03:21.457] [main] 5050 포트에서 연결을 기다립니다
 * ② 단계부터는 스레드 이름(client-1, client-2 ...)으로 어느 연결의 로그인지 구분할 수 있다.
 */
final class Log {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {
    }

    /** 진행 상황을 표준 출력에 남긴다. */
    static void info(String message) {
        System.out.println(line(message));
    }

    /** 오류를 표준 오류에 남긴다. */
    static void error(String message) {
        System.err.println(line("[오류] " + message));
    }

    /** 한 줄을 통째로 만든 뒤 한 번에 출력해야 여러 스레드의 로그가 섞이지 않는다. */
    private static String line(String message) {
        return "[" + LocalTime.now().format(TIME) + "] [" + Thread.currentThread().getName() + "] " + message;
    }
}
