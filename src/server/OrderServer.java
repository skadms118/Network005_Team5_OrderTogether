package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 서버 본체. 강의 자료의 TCP 서버 순서를 따른다.
 *   1. TCP 소켓 만들기 (포트 열기)  → open()
 *   2. 반복: 연결 대기·수락          → serve()
 * 연결을 받으면 클라이언트마다 스레드를 하나씩 띄워 ClientHandler에 맡긴다 (멀티스레드 서버, 강의 p.48).
 * 받은 요청은 CommandRouter가 나누고, 방은 RoomManager가 관리한다.
 */
final class OrderServer {

    private final int port;
    private final AtomicInteger nextId = new AtomicInteger(1);                 // 연결 번호: client-1, client-2 ...
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();  // 접속 중인 연결 (여러 스레드가 동시에 넣고 뺌)
    private final TeamHooks hooks = new TeamHooks();                          // 2번·3번 연결 자리
    private final RoomManager rooms = new RoomManager(this, hooks);
    private final CommandRouter router = new CommandRouter(rooms, hooks);
    private volatile ServerSocket serverSocket;  // welcoming socket: 접속 요청을 기다리는 소켓
    private volatile boolean running;

    OrderServer(int port) {
        this.port = port;
    }

    /** 요청 분배기. 각 연결의 스레드가 받은 줄을 넘길 때 쓴다. */
    CommandRouter router() {
        return router;
    }

    /** 방 관리자. 2번이 마감 뒤 로비에 방 목록을 보낼 때(pushRoomList) 쓴다. */
    RoomManager rooms() {
        return rooms;
    }

    /** 로비(방에 없는 연결) 전원에게 한 줄을 보낸다. 방 락 안에서 부르지 않는다. */
    void broadcastLobby(String line) {
        for (ClientHandler client : clients) {
            if (client.room() == null) {
                client.send(line);
            }
        }
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

    /**
     * 받은 연결을 새 스레드에 맡긴다.
     * accept 반복이 멈추지 않도록 여기서는 읽기·쓰기를 하지 않는다 (강의 p.47의 문제 방지).
     */
    private void handle(Socket socket) {
        ClientHandler handler;
        try {
            handler = new ClientHandler(nextId.getAndIncrement(), socket, this);
        } catch (IOException e) {
            Log.error("연결 준비 실패: " + socket.getRemoteSocketAddress() + " (" + e.getMessage() + ")");
            try {
                socket.close();
            } catch (IOException ignored) {
                // 이미 끊긴 소켓이라 더 할 일 없음
            }
            return;
        }
        clients.add(handler);
        Log.info("접속: " + socket.getRemoteSocketAddress() + " (" + handler + ", 현재 " + clients.size() + "명)");
        new Thread(handler, handler.toString()).start();
    }

    /**
     * 연결 하나가 끝났을 때 그 연결의 스레드가 부른다 (ClientHandler.run()의 finally).
     * 방에 있던 연결이면 방에서 내보낸다 (기본 단계: LEAVE와 같게 처리, 방장이면 ROOM_END).
     * 재접속 확장을 붙일 때 3번의 세션 정리·유예 처리를 여기서 부른다.
     */
    void onDisconnect(ClientHandler handler) {
        if (clients.remove(handler)) {
            Log.info("접속 종료 (현재 " + clients.size() + "명)");
        }
        if (handler.room() != null) {
            rooms.onConnectionLost(handler);
        }
    }

    /**
     * 서버를 멈추고 포트를 반납한다. 종료 훅(Ctrl+C) 스레드에서 불린다.
     * 남아 있는 연결도 모두 닫는다. 여러 번 불려도 처음 한 번만 동작한다.
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
        for (ClientHandler handler : clients) {
            handler.close();       // 각 연결의 스레드가 readLine()에서 깨어나 스스로 정리한다
        }
        Log.info("서버를 종료했습니다");
    }
}
