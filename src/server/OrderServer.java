package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * 서버 본체. 강의 자료의 TCP 서버 순서를 따른다.
 *   1. TCP 소켓 만들기 (포트 열기)  → open()
 *   2. 반복: 연결 대기·수락          → serve()
 * ① 서버 실행 단계에서는 연결을 받으면 기록만 남기고 바로 닫는다.
 * ② 다중 접속 단계에서 handle()을 "연결마다 ClientHandler 스레드 실행"으로 바꾼다.
 */
final class OrderServer {

    private final int port;
    private volatile ServerSocket serverSocket;  // welcoming socket: 접속 요청을 기다리는 소켓
    private volatile boolean running;

    OrderServer(int port) {
        this.port = port;
    }

    /**
     * 포트를 연다. Java는 ServerSocket을 만들 때 bind까지 하므로 포트 충돌은 여기서 드러난다.
     * 주소를 지정하지 않아 모든 네트워크 주소에서 접속을 받는다 (다른 PC에서도 접속 가능).
     *
     * @throws java.net.BindException 포트가 이미 사용 중일 때
     * @throws IOException 그 밖의 이유로 포트를 열지 못했을 때
     */
    void open() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        Log.info(port + " 포트에서 연결을 기다립니다 (종료: Ctrl+C)");
    }

    /**
     * 연결 대기·수락을 반복한다. shutdown()이 불릴 때까지 돌아오지 않는다.
     * accept()는 연결이 올 때까지 멈춰 있으므로, 이 반복문은 연결을 받는 일만 한다.
     */
    void serve() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();  // connection socket: 클라이언트마다 새로 생긴다
            } catch (IOException e) {
                if (!running) {
                    break;                       // shutdown()이 서버 소켓을 닫음: 정상 종료
                }
                Log.error("연결 수락 실패: " + e.getMessage());
                continue;                        // 연결 하나의 실패로 서버 전체가 멈추지 않게
            }
            handle(socket);
        }
    }

    /** ① 단계 임시 처리: 접속 기록만 남기고 연결을 닫는다. */
    private void handle(Socket socket) {
        Log.info("접속: " + socket.getRemoteSocketAddress() + " (① 단계라 바로 닫음)");
        try {
            socket.close();
        } catch (IOException e) {
            Log.error("연결 닫기 실패: " + e.getMessage());
        }
    }

    /**
     * 서버를 멈추고 포트를 반납한다. 종료 훅(Ctrl+C) 스레드에서 불린다.
     * 여러 번 불려도 처음 한 번만 동작한다.
     */
    synchronized void shutdown() {
        if (!running) {
            return;
        }
        running = false;
        try {
            serverSocket.close();  // accept()에서 기다리던 스레드가 예외로 깨어나 반복을 끝낸다
        } catch (IOException e) {
            Log.error("서버 소켓 닫기 실패: " + e.getMessage());
        }
        Log.info("서버를 종료했습니다");
    }
}
