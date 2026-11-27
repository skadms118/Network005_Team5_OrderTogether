package server;

import common.ErrorCode;
import common.Protocol;
import common.RoomInfo;
import common.StatusInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 방 목록과 방 생성·입장·퇴장을 맡는다 (1번: 방 관리).
 *
 * 규칙
 *   - 방 상태를 바꾸는 일과 그 방 사람에게 보내는 일은 방 락(synchronized (room)) 안에서 한다
 *   - 로비 ROOM_LIST 전송은 방 락을 놓은 뒤에 한다 (여러 방의 락을 한꺼번에 잡지 않기)
 *   - 2번·3번 코드가 필요한 자리는 TeamHooks로 부른다
 */
final class RoomManager {

    private final OrderServer server;
    private final TeamHooks hooks;
    private final Map<Integer, Room> rooms = new ConcurrentHashMap<>();
    private final AtomicInteger nextRoomId = new AtomicInteger(1);  // 방 번호는 다시 쓰지 않는다

    RoomManager(OrderServer server, TeamHooks hooks) {
        this.server = server;
        this.hooks = hooks;
    }

    /** ROOMS: 요청한 사람에게 방 목록을 보낸다. */
    void sendRoomList(ClientHandler client) {
        client.send(roomListLine());
    }

    /**
     * 로비(방에 없는 사람) 전원에게 방 목록을 보낸다. 방이 바뀔 때마다 부른다 (제안서 p.10, p.12).
     * 2번도 마감(CLOSE) 뒤 방 락을 놓은 다음 이 메서드를 부르면 된다. 방 락 안에서 부르지 않는다.
     */
    void pushRoomList() {
        server.broadcastLobby(roomListLine());
    }

    private String roomListLine() {
        List<RoomInfo> list = new ArrayList<>();
        for (Room room : new TreeMap<>(rooms).values()) {  // 방 번호 순서
            list.add(room.toInfo());
        }
        return Protocol.roomList(list);
    }

    /**
     * CREATE: 방을 만들고 만든 사람을 방장으로 넣는다.
     * 응답: OK|CREATE|방번호|토큰 → (MENU) → STATUS|||0, 그 뒤 로비에 ROOM_LIST
     */
    void create(ClientHandler client, String nick, String name) {
        if (!Protocol.isValidName(nick) || !Protocol.isValidName(name)) {  // 제안서 p.8, p.11: 빈 이름, | , : 금지
            client.send(Protocol.error(ErrorCode.INVALID_NAME));
            return;
        }
        Room room = new Room(nextRoomId.getAndIncrement(), name, nick, client);
        synchronized (room) {  // 응답을 다 보내기 전에 다른 사람이 들어와 순서가 꼬이지 않게
            rooms.put(room.id(), room);
            client.enterRoom(room, nick);
            client.send(Protocol.ok(Protocol.CREATE, String.valueOf(room.id()), hooks.issueToken(client, room, nick)));
            sendIfPresent(client, hooks.menuLine());
            client.send(Protocol.status(StatusInfo.empty()));  // 새 방은 주문이 없으므로 항상 빈 현황
        }
        Log.info("방 생성: " + room.id() + "번 " + name + " (방장 " + nick + ")");
        pushRoomList();
    }

    /**
     * JOIN: 방에 들어간다. 검사와 추가를 방 락 안에서 한 번에 처리해서,
     * 여러 명이 동시에 들어와도 정원(5명)을 넘거나 같은 닉네임이 두 번 들어가지 않는다 (⑤).
     * 응답: OK|JOIN|토큰 → (MENU) → (STATUS), 그 뒤 로비에 ROOM_LIST
     */
    void join(ClientHandler client, int roomId, String nick) {
        if (!Protocol.isValidName(nick)) {
            client.send(Protocol.error(ErrorCode.INVALID_NAME));
            return;
        }
        Room room = rooms.get(roomId);
        if (room == null) {
            client.send(Protocol.error(ErrorCode.NO_ROOM));
            return;
        }
        synchronized (room) {
            ErrorCode error = null;
            if (room.isDeleted()) {
                error = ErrorCode.NO_ROOM;       // 방장이 막 나가 없어진 방
            } else if (room.isClosed()) {
                error = ErrorCode.CLOSED;        // 제안서 p.11: 마감 후 입장 불가
            } else if (room.isFull()) {
                error = ErrorCode.ROOM_FULL;
            } else if (room.hasMember(nick)) {
                error = ErrorCode.DUP_NICK;      // 같은 방 안에서만 검사
            }
            if (error != null) {
                client.send(Protocol.error(error));
                return;
            }
            room.add(nick, client);
            client.enterRoom(room, nick);
            client.send(Protocol.ok(Protocol.JOIN, hooks.issueToken(client, room, nick)));
            sendIfPresent(client, hooks.menuLine());
            sendIfPresent(client, hooks.statusLine(room));
        }
        Log.info("입장: " + room.id() + "번 방에 " + nick + " (" + room.size() + "/" + Room.MAX_MEMBERS + ")");
        pushRoomList();
    }

    /** LEAVE: 방에서 나간다. 멤버는 OK|LEAVE, 방장은 방 전원과 함께 ROOM_END를 받는다. */
    void leave(ClientHandler client) {
        exit(client, true);
    }

    /**
     * 연결이 끊긴 사람을 방에서 내보낸다. 기본 단계에서는 LEAVE와 같게 처리한다 (방장이면 ROOM_END).
     * 재접속 확장(60초 유예)을 붙일 때는 3번과 정한 방식으로 이 부분을 바꾼다.
     */
    void onConnectionLost(ClientHandler client) {
        exit(client, false);
    }

    /**
     * 퇴장 처리 (제안서 p.9, p.12).
     *   멤버: 남은 사람에게 STATUS, 나간 사람에게 OK|LEAVE
     *   방장: 방장 포함 방 전원에게 ROOM_END ("LEAVE → ROOM_END to all"), 방 삭제
     */
    private void exit(ClientHandler client, boolean reply) {
        Room room = client.room();
        String nick = client.nickname();
        if (room == null) {  // 방장이 먼저 나가 이미 로비로 돌아온 경우 (ROOM_END를 이미 받음)
            if (reply) {
                client.send(Protocol.ok(Protocol.LEAVE));
            }
            return;
        }
        String result;
        synchronized (room) {
            if (!room.hasMember(nick)) {  // 방장이 먼저 나가 이미 정리된 경우
                client.leaveRoom();
                if (reply) {
                    client.send(Protocol.ok(Protocol.LEAVE));
                }
                return;
            }
            if (room.isHost(nick)) {
                room.markDeleted();
                rooms.remove(room.id());
                room.broadcast(Protocol.ROOM_END);  // 방장 포함 전원
                for (ClientHandler member : room.connections()) {
                    member.leaveRoom();
                }
                room.clearMembers();
                hooks.onRoomEnded(room);
                result = "방장 " + nick + " 퇴장, " + room.id() + "번 방 종료";
            } else {
                room.remove(nick);
                hooks.onMemberLeft(room, nick);                    // 2번: 마감 전이면 나간 사람 주문 삭제
                broadcastIfPresent(room, hooks.statusLine(room));  // 남은 사람에게 바뀐 현황
                client.leaveRoom();
                if (reply) {
                    client.send(Protocol.ok(Protocol.LEAVE));
                }
                result = nick + " 퇴장, " + room.id() + "번 방 " + room.size() + "/" + Room.MAX_MEMBERS;
            }
        }
        Log.info(result + (reply ? "" : " (연결 끊김)"));
        pushRoomList();
    }

    private static void sendIfPresent(ClientHandler client, String line) {
        if (line != null) {
            client.send(line);
        }
    }

    private static void broadcastIfPresent(Room room, String line) {
        if (line != null) {
            room.broadcast(line);
        }
    }
}
