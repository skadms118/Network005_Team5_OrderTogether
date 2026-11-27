package server;

import common.ErrorCode;
import common.MenuItem;
import common.Message;
import common.Protocol;
import common.StatusInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 2번 담당: 주문·마감 관리.
 *   - 고정 메뉴판 (MENU)
 *   - ORDER · CANCEL · CLEAR · CLOSE 처리
 *   - 사람별 주문, 메뉴별 수량, 총금액 집계 (STATUS · FINAL)
 *   - 나간 사람 주문 정리
 *
 * 동기화: 주문을 바꾸거나 읽을 때는 항상 방 락(synchronized (room))을 잡는다.
 *   1번 방 관리 코드와 같은 락을 쓰므로, "마감하는 순간 들어온 주문"이나
 *   "주문하는 순간 나간 사람" 같은 경우에도 순서가 꼬이지 않는다.
 *   STATUS도 같은 락 안에서 만들고 보내므로, 반쯤 바뀐 현황이 전송되지 않는다.
 */
final class OrderManager {

    /** 한 메뉴를 한 번에 주문할 수 있는 최대 수량 (너무 큰 수로 금액이 넘치는 것 방지) */
    static final int MAX_QTY = 99;

    /** 고정 메뉴판 (메뉴ID → 메뉴). 이름에 | , : 를 쓰면 안 된다. */
    private static final Map<Integer, MenuItem> MENU = new LinkedHashMap<>();
    static {
        add(1, "후라이드치킨", 18000);
        add(2, "양념치킨", 19000);
        add(3, "간장치킨", 19000);
        add(4, "반반치킨", 19000);
        add(5, "치즈볼", 5000);
        add(6, "감자튀김", 4000);
        add(7, "콜라 1.25L", 2500);
        add(8, "사이다 1.25L", 2500);
    }

    private static void add(int id, String name, int price) {
        MENU.put(id, new MenuItem(id, name, price));
    }

    /** 방별 주문: 방 → (닉네임 → (메뉴ID → 수량)). 안쪽 Map은 방 락 안에서만 만진다. */
    private final Map<Room, Map<String, Map<Integer, Integer>>> orders = new ConcurrentHashMap<>();

    // ===================== 메뉴 =====================

    /** MENU|1:후라이드치킨:18000,... 한 줄 */
    String menuLine() {
        return Protocol.menu(List.copyOf(MENU.values()));
    }

    // ===================== 요청 처리 =====================

    /**
     * ORDER · CANCEL · CLEAR · CLOSE 하나를 처리한다. (방 안에 있는지는 CommandRouter가 이미 확인함)
     * 성공하면 요청한 사람에게 OK|명령, 방 전체에 STATUS(마감이면 FINAL)를 보낸다.
     * 실패하면 요청한 사람에게만 ERR|코드를 보낸다.
     *
     * @return 마감이 성공해서 로비의 방 목록을 다시 보내야 하면 true
     */
    boolean handle(ClientHandler client, Room room, Message message) {
        synchronized (room) {
            String nick = client.nickname();
            if (room.isDeleted() || !room.hasMember(nick)) {
                client.send(Protocol.error(ErrorCode.NOT_IN_ROOM));  // 처리 직전에 방이 없어진 경우
                return false;
            }
            return switch (message.command) {
                case Protocol.ORDER -> order(client, room, nick, message.field(0));
                case Protocol.CANCEL -> cancel(client, room, nick);
                case Protocol.CLEAR -> clear(client, room, nick);
                case Protocol.CLOSE -> close(client, room, nick);
                default -> {
                    client.send(Protocol.error(ErrorCode.BAD_REQUEST));
                    yield false;
                }
            };
        }
    }

    /** ORDER|1:2,3:1 : 내 주문을 통째로 바꾼다 (전체 교체 방식이라 같은 요청이 두 번 와도 결과가 같다). */
    private boolean order(ClientHandler client, Room room, String nick, String text) {
        if (room.isClosed()) {
            client.send(Protocol.error(ErrorCode.CLOSED));
            return false;
        }
        Map<Integer, Integer> parsed = Protocol.parseOrder(text);  // 형식 오류·수량 0 이하·같은 메뉴 반복이면 null
        if (parsed == null || parsed.isEmpty() || !isValidOrder(parsed)) {
            client.send(Protocol.error(ErrorCode.INVALID_MENU));
            return false;
        }
        roomOrders(room).put(nick, new TreeMap<>(parsed));  // 메뉴 번호 순으로 저장
        client.send(Protocol.ok(Protocol.ORDER));
        room.broadcast(statusLine(room));
        return false;
    }

    /** CANCEL : 내 주문을 지운다. */
    private boolean cancel(ClientHandler client, Room room, String nick) {
        if (room.isClosed()) {
            client.send(Protocol.error(ErrorCode.CLOSED));
            return false;
        }
        roomOrders(room).remove(nick);
        client.send(Protocol.ok(Protocol.CANCEL));
        room.broadcast(statusLine(room));
        return false;
    }

    /** CLEAR : 방 전체 주문을 지운다 (방장만). */
    private boolean clear(ClientHandler client, Room room, String nick) {
        if (!room.isHost(nick)) {
            client.send(Protocol.error(ErrorCode.NOT_HOST));
            return false;
        }
        if (room.isClosed()) {
            client.send(Protocol.error(ErrorCode.CLOSED));
            return false;
        }
        roomOrders(room).clear();
        client.send(Protocol.ok(Protocol.CLEAR));
        room.broadcast(statusLine(room));
        return false;
    }

    /** CLOSE : 주문을 마감하고 최종 주문표(FINAL)를 보낸다 (방장만). */
    private boolean close(ClientHandler client, Room room, String nick) {
        if (!room.isHost(nick)) {
            client.send(Protocol.error(ErrorCode.NOT_HOST));
            return false;
        }
        if (room.isClosed()) {
            client.send(Protocol.error(ErrorCode.CLOSED));  // 이미 마감됨
            return false;
        }
        room.setClosed(true);
        client.send(Protocol.ok(Protocol.CLOSE));
        room.broadcast(Protocol.finalOrder(summarize(room)));
        return true;  // 방 상태가 OPEN → CLOSED로 바뀌었으니 로비 목록 갱신 필요
    }

    // ===================== 집계 =====================

    /** 이 방의 현재 STATUS 한 줄. 방 락 안에서 불러야 한다. */
    String statusLine(Room room) {
        return Protocol.status(summarize(room));
    }

    /** 사람별 주문, 메뉴별 수량, 총금액을 계산한다 (방 락 안). */
    private StatusInfo summarize(Room room) {
        Map<String, Map<Integer, Integer>> mine = roomOrders(room);
        Map<String, Map<Integer, Integer>> byUser = new LinkedHashMap<>();
        Map<Integer, Integer> counts = new TreeMap<>();
        int total = 0;
        for (String name : room.memberNames()) {  // 방에 들어온 순서대로
            Map<Integer, Integer> order = mine.get(name);
            if (order == null || order.isEmpty()) {
                continue;                          // 주문 안 한 사람은 STATUS에 넣지 않음
            }
            byUser.put(name, new LinkedHashMap<>(order));  // 복사본을 넘긴다
            for (Map.Entry<Integer, Integer> e : order.entrySet()) {
                counts.merge(e.getKey(), e.getValue(), Integer::sum);
                total += MENU.get(e.getKey()).price * e.getValue();
            }
        }
        return new StatusInfo(byUser, new LinkedHashMap<>(counts), total);
    }

    // ===================== 퇴장 · 방 종료 =====================

    /** 멤버가 나갔을 때 (방 락 안). 마감 전이면 그 사람 주문을 지운다. 마감 후에는 최종 주문표를 지키기 위해 남긴다. */
    void onMemberLeft(Room room, String nickname) {
        if (!room.isClosed()) {
            roomOrders(room).remove(nickname);
        }
    }

    /** 방장이 나가 방이 없어졌을 때 (방 락 안). 그 방 주문을 모두 정리한다. */
    void onRoomEnded(Room room) {
        orders.remove(room);
    }

    // ===================== 도우미 =====================

    private Map<String, Map<Integer, Integer>> roomOrders(Room room) {
        return orders.computeIfAbsent(room, r -> new LinkedHashMap<>());
    }

    /** 메뉴판에 있는 메뉴이고 수량이 1~MAX_QTY인지 확인한다. */
    private static boolean isValidOrder(Map<Integer, Integer> order) {
        for (Map.Entry<Integer, Integer> e : order.entrySet()) {
            if (!MENU.containsKey(e.getKey()) || e.getValue() < 1 || e.getValue() > MAX_QTY) {
                return false;
            }
        }
        return true;
    }
}
