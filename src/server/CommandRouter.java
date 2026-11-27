package server;

import common.ErrorCode;
import common.Message;
import common.Protocol;

import java.util.Map;

/**
 * 요청 분배 (③). 받은 한 줄을 해석하고, 형식과 방 안·밖을 검사한 뒤 담당 코드로 넘긴다.
 *   ROOMS·CREATE·JOIN·LEAVE   → 1번 RoomManager
 *   ORDER·CANCEL·CLEAR·CLOSE  → 2번 (TeamHooks를 거쳐서)
 *   RECONNECT·PONG            → 3번 (TeamHooks를 거쳐서)
 */
final class CommandRouter {

    /**
     * 명령별 필드 개수. 모르는 명령이거나 개수가 모자라거나 남으면 ERR|BAD_REQUEST.
     * 단, CREATE·JOIN에서 필드가 남는 것은 이름에 '|'가 들어간 경우뿐이라 제안서대로 ERR|INVALID_NAME (p.8, p.11).
     */
    private static final Map<String, Integer> FIELD_COUNT = Map.of(
            Protocol.ROOMS, 0,
            Protocol.CREATE, 2,      // CREATE|닉네임|방이름
            Protocol.JOIN, 2,        // JOIN|방번호|닉네임
            Protocol.ORDER, 1,       // ORDER|1:2,3:1
            Protocol.CANCEL, 0,
            Protocol.CLEAR, 0,
            Protocol.CLOSE, 0,
            Protocol.LEAVE, 0,
            Protocol.RECONNECT, 1,   // RECONNECT|토큰
            Protocol.PONG, 0);

    private final RoomManager rooms;
    private final TeamHooks hooks;

    CommandRouter(RoomManager rooms, TeamHooks hooks) {
        this.rooms = rooms;
        this.hooks = hooks;
    }

    /** 한 줄을 처리한다. 이 연결의 스레드(ClientHandler.run)에서 불린다. */
    void dispatch(ClientHandler client, String line) {
        Message message = Protocol.parse(line);
        Integer expected = FIELD_COUNT.get(message.command);
        if (expected == null) {
            reject(client, ErrorCode.BAD_REQUEST);       // 모르는 명령
            return;
        }
        if (message.size() != expected) {
            boolean nameHasBar = message.size() > expected
                    && (message.command.equals(Protocol.CREATE) || message.command.equals(Protocol.JOIN));
            reject(client, nameHasBar ? ErrorCode.INVALID_NAME : ErrorCode.BAD_REQUEST);
            return;
        }

        Room room = client.room();  // null이면 로비
        switch (message.command) {
            case Protocol.ROOMS -> rooms.sendRoomList(client);
            case Protocol.CREATE -> {
                if (room != null) {
                    reject(client, ErrorCode.ALREADY_IN_ROOM);  // 1 연결 = 1 방 (제안서 p.8)
                } else {
                    rooms.create(client, message.field(0), message.field(1));
                }
            }
            case Protocol.JOIN -> {
                Integer roomId = parseRoomId(message.field(0));
                if (room != null) {
                    reject(client, ErrorCode.ALREADY_IN_ROOM);
                } else if (roomId == null) {
                    reject(client, ErrorCode.BAD_REQUEST);      // 방 번호가 숫자가 아님
                } else {
                    rooms.join(client, roomId, message.field(1));
                }
            }
            case Protocol.LEAVE -> {
                if (room == null) {
                    reject(client, ErrorCode.NOT_IN_ROOM);
                } else {
                    rooms.leave(client);
                }
            }
            case Protocol.ORDER, Protocol.CANCEL, Protocol.CLEAR, Protocol.CLOSE -> {
                if (room == null) {
                    reject(client, ErrorCode.NOT_IN_ROOM);      // 방 안 요청은 소속부터 확인 (제안서 p.9)
                } else {
                    hooks.orderCommand(client, room, message);
                }
            }
            case Protocol.RECONNECT -> {
                if (room != null) {
                    reject(client, ErrorCode.ALREADY_IN_ROOM);
                } else {
                    hooks.reconnect(client, message.field(0));
                }
            }
            case Protocol.PONG -> hooks.pong(client);
            default -> reject(client, ErrorCode.BAD_REQUEST);  // FIELD_COUNT에 있는 명령만 여기까지 온다
        }
    }

    private static void reject(ClientHandler client, ErrorCode code) {
        client.send(Protocol.error(code));
    }

    private static Integer parseRoomId(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
