package server;

import common.ErrorCode;
import common.Protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 클라이언트 연결 하나를 맡는 작업. 연결 1개 = ClientHandler 1개 = 스레드 1개.
 * OrderServer가 new Thread(handler, "client-N").start()로 실행한다 (강의 p.53, Runnable 방식).
 *
 * 받은 줄은 CommandRouter로 넘긴다 (③ 요청 분배).
 * 이 연결이 있는 방과 닉네임을 기억한다 (방이 null이면 로비).
 */
final class ClientHandler implements Runnable {

    private final int id;
    private final Socket socket;  // connection socket: 이 클라이언트 전용 소켓
    private final OrderServer server;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private volatile Room room;          // 지금 들어가 있는 방 (null이면 로비). 방장이 나가면 다른 스레드가 바꾸므로 volatile
    private volatile String nickname;    // 방에서 쓰는 닉네임 (CREATE·JOIN 때 정해짐)

    /**
     * 소켓에 UTF-8 읽기·쓰기 스트림을 연결한다.
     * 강의 예제의 DataOutputStream.writeBytes()는 한글을 깨뜨리므로 쓰지 않는다.
     */
    ClientHandler(int id, Socket socket, OrderServer server) throws IOException {
        this.id = id;
        this.socket = socket;
        this.server = server;
        socket.setTcpNoDelay(true);  // 짧은 메시지를 모아 보내느라 늦어지지 않게 (Nagle 알고리즘 끄기)
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** 스레드 본체: 한 줄씩 읽어 처리한다. 연결이 끝나면 정리하고 스레드도 끝난다. */
    @Override
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {  // null: 상대가 연결을 닫음 (EOF)
                if (line.isBlank()) {
                    continue;
                }
                Log.info("받음: " + forLog(line));
                try {
                    server.router().dispatch(this, line);
                } catch (RuntimeException e) {         // 메시지 하나가 실패해도 연결은 유지
                    Log.error("처리 실패: " + forLog(line) + " (" + e + ")");
                    send(Protocol.error(ErrorCode.BAD_REQUEST));
                }
            }
        } catch (IOException e) {
            // 연결이 강제로 끊겼거나(Connection reset) 다른 스레드가 close()를 부른 경우
        } finally {
            close();
            server.onDisconnect(this);                 // 정리는 여기서만, 연결마다 딱 한 번
        }
    }

    /** 로그에 남길 형태. 재접속 토큰은 로그에 남기지 않는다. */
    private static String forLog(String line) {
        return line.startsWith(Protocol.RECONNECT + "|") ? Protocol.RECONNECT + "|(토큰 생략)" : line;
    }

    /** 지금 들어가 있는 방 (null이면 로비) */
    Room room() {
        return room;
    }

    /** 방에서 쓰는 닉네임 */
    String nickname() {
        return nickname;
    }

    /** 방에 들어갔을 때 RoomManager가 부른다 (방 락 안). */
    void enterRoom(Room room, String nickname) {
        this.nickname = nickname;
        this.room = room;
    }

    /** 방에서 나왔을 때(LEAVE, ROOM_END, 연결 끊김) RoomManager가 부른다 (방 락 안). */
    void leaveRoom() {
        this.room = null;
    }

    /**
     * 한 줄을 보낸다. 여러 스레드가 동시에 불러도 줄이 섞이지 않게 synchronized로 막는다.
     * println()은 Windows에서 \r\n을 붙이므로 쓰지 않고 \n을 직접 붙인다.
     */
    synchronized void send(String line) {
        if (closed.get()) {
            return;
        }
        try {
            out.write(line);
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            close();  // 보낼 수 없는 연결은 닫는다. 정리는 이 연결의 스레드가 finally에서 한다
        }
    }

    /** 연결을 닫는다. 몇 번, 어느 스레드에서 불러도 한 번만 동작한다. */
    void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                socket.close();  // 이 연결의 스레드가 readLine()에서 기다리던 중이면 예외로 깨어난다
            } catch (IOException ignored) {
                // 이미 끊긴 소켓이라 더 할 일 없음
            }
        }
    }

    /** 로그와 스레드 이름에 쓰는 이름 (client-1, client-2 ...). */
    @Override
    public String toString() {
        return "client-" + id;
    }
}
