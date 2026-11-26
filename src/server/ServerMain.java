package server;

import java.io.IOException;
import java.net.BindException;

/**
 * 서버 시작점.
 * 실행: java -cp out server.ServerMain [포트]   (포트를 생략하면 5050)
 * 포트 인자를 확인하고, 포트를 열고, 종료 처리를 등록한 뒤 연결 대기를 시작한다.
 */
public final class ServerMain {

    static final int DEFAULT_PORT = 5050;

    private ServerMain() {
    }

    public static void main(String[] args) {
        int port = parsePort(args);
        if (port == -1) {
            printUsage();
            System.exit(1);
        }

        OrderServer server = new OrderServer(port);
        try {
            server.open();
        } catch (BindException e) {
            Log.error(port + " 포트가 이미 사용 중입니다. 켜져 있는 서버가 있는지 확인하거나 다른 포트로 실행하세요."
                    + " (Windows 확인 명령: netstat -ano -p tcp | findstr :" + port + ")");
            System.exit(1);
        } catch (IOException e) {
            Log.error("서버를 시작하지 못했습니다: " + e.getMessage());
            System.exit(1);
        }

        // Ctrl+C나 창 닫기로 끝날 때 서버 소켓을 닫아 포트를 반납한다
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdown, "shutdown"));

        server.serve();
    }

    /**
     * 포트 인자를 읽는다.
     * 인자가 없으면 기본 포트, 1~65535 사이 숫자 하나면 그 값, 그 밖에는 -1.
     */
    static int parsePort(String[] args) {
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        if (args.length > 1) {
            return -1;
        }
        try {
            int port = Integer.parseInt(args[0].trim());
            return (port >= 1 && port <= 65535) ? port : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 잘못된 인자일 때 사용법을 보여 준다. */
    private static void printUsage() {
        System.err.println("사용법: java -cp out server.ServerMain [포트]");
        System.err.println("  포트: 1~65535 사이의 숫자 (생략하면 " + DEFAULT_PORT + ")");
    }
}
