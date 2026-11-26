package common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * 메시지 형식: 명령|필드1|필드2...  (한 줄 = 메시지 하나)
 * 항목 구분 ','  값 구분 ':'
 */
public class Protocol {

    // client -> server
    public static final String ROOMS = "ROOMS";
    public static final String CREATE = "CREATE";
    public static final String JOIN = "JOIN";
    public static final String ORDER = "ORDER";
    public static final String CANCEL = "CANCEL";
    public static final String CLEAR = "CLEAR";
    public static final String CLOSE = "CLOSE";
    public static final String LEAVE = "LEAVE";
    public static final String RECONNECT = "RECONNECT";
    public static final String PONG = "PONG";

    // server -> client
    public static final String OK = "OK";
    public static final String ERR = "ERR";
    public static final String ROOM_LIST = "ROOM_LIST";
    public static final String MENU = "MENU";
    public static final String STATUS = "STATUS";
    public static final String FINAL = "FINAL";
    public static final String ROOM_END = "ROOM_END";
    public static final String PING = "PING";

    public static Message parse(String line) {
        if (line == null) {
            return new Message("", new String[0]);
        }
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        // -1: 빈 필드 유지 (STATUS|||0)
        String[] parts = line.split("\\|", -1);
        String[] fields = new String[parts.length - 1];
        System.arraycopy(parts, 1, fields, 0, fields.length);
        return new Message(parts[0], fields);
    }

    public static String build(String command, String... fields) {
        StringBuilder sb = new StringBuilder(command);
        for (String f : fields) {
            sb.append('|').append(f);
        }
        return sb.toString();
    }

    // ok(CREATE, "1", "tk_a1") -> OK|CREATE|1|tk_a1
    public static String ok(String command, String... extra) {
        String[] fields = new String[extra.length + 1];
        fields[0] = command;
        System.arraycopy(extra, 0, fields, 1, extra.length);
        return build(OK, fields);
    }

    // error(ErrorCode.ROOM_FULL) -> ERR|ROOM_FULL
    public static String error(ErrorCode code) {
        return build(ERR, code.name());
    }

    // ERR 메시지 -> ErrorCode (모르는 코드면 null)
    public static ErrorCode errorCodeOf(Message m) {
        try {
            return ErrorCode.valueOf(m.field(0));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // "1:2,3:1" -> {1=2, 3=1}, 형식 오류/수량 0 이하면 null
    // 메뉴 ID 존재 여부는 서버에서 확인
    public static Map<Integer, Integer> parseOrder(String text) {
        Map<Integer, Integer> order = new LinkedHashMap<>();
        if (text == null || text.isEmpty()) {
            return order;
        }
        for (String item : text.split(",", -1)) {
            String[] kv = item.split(":", -1);
            if (kv.length != 2) {
                return null;
            }
            try {
                int menuId = Integer.parseInt(kv[0].trim());
                int qty = Integer.parseInt(kv[1].trim());
                if (qty <= 0 || order.containsKey(menuId)) {
                    return null;
                }
                order.put(menuId, qty);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return order;
    }

    public static String formatOrder(Map<Integer, Integer> order) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : order.entrySet()) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        return sb.toString();
    }

    // 닉네임/방이름: 비어있거나 | , : 포함이면 false
    public static boolean isValidName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return !(name.contains("|") || name.contains(",") || name.contains(":"));
    }

    // ROOM_LIST|1:치킨방:3/5:OPEN,2:피자방:5/5:CLOSED
    public static String roomList(List<RoomInfo> rooms) {
        StringBuilder sb = new StringBuilder();
        for (RoomInfo r : rooms) {
            if (sb.length() > 0) sb.append(',');
            sb.append(r.id).append(':').append(r.name).append(':')
              .append(r.users).append('/').append(r.max).append(':')
              .append(r.closed ? "CLOSED" : "OPEN");
        }
        return build(ROOM_LIST, sb.toString());
    }

    // MENU|1:후라이드:18000,2:양념:19000
    public static String menu(List<MenuItem> items) {
        StringBuilder sb = new StringBuilder();
        for (MenuItem m : items) {
            if (sb.length() > 0) sb.append(',');
            sb.append(m.id).append(':').append(m.name).append(':').append(m.price);
        }
        return build(MENU, sb.toString());
    }

    // STATUS|A:1:2,A:3:1,B:2:1|1:2,2:1,3:1|57000
    // 한 사람이 여러 메뉴 -> 이름 반복 (A:1:2,A:3:1)
    public static String status(StatusInfo s) {
        return statusLike(STATUS, s);
    }

    public static String finalOrder(StatusInfo s) {
        return statusLike(FINAL, s);
    }

    private static String statusLike(String command, StatusInfo s) {
        StringBuilder users = new StringBuilder();
        for (Map.Entry<String, Map<Integer, Integer>> u : s.byUser.entrySet()) {
            for (Map.Entry<Integer, Integer> item : u.getValue().entrySet()) {
                if (users.length() > 0) users.append(',');
                users.append(u.getKey()).append(':').append(item.getKey()).append(':').append(item.getValue());
            }
        }
        return build(command, users.toString(), formatOrder(s.counts), String.valueOf(s.total));
    }

    public static List<RoomInfo> parseRoomList(Message m) {
        List<RoomInfo> rooms = new ArrayList<>();
        if (m.field(0).isEmpty()) {
            return rooms;
        }
        for (String item : m.field(0).split(",", -1)) {
            String[] p = item.split(":", -1);
            if (p.length != 4) continue;
            String[] cnt = p[2].split("/", -1);
            try {
                rooms.add(new RoomInfo(Integer.parseInt(p[0]), p[1],
                        Integer.parseInt(cnt[0]), Integer.parseInt(cnt[1]), "CLOSED".equals(p[3])));
            } catch (RuntimeException e) {
                // 잘못된 항목은 건너뜀
            }
        }
        return rooms;
    }

    public static List<MenuItem> parseMenu(Message m) {
        List<MenuItem> items = new ArrayList<>();
        if (m.field(0).isEmpty()) {
            return items;
        }
        for (String item : m.field(0).split(",", -1)) {
            String[] p = item.split(":", -1);
            if (p.length != 3) continue;
            try {
                items.add(new MenuItem(Integer.parseInt(p[0]), p[1], Integer.parseInt(p[2])));
            } catch (NumberFormatException e) {
                // 잘못된 항목은 건너뜀
            }
        }
        return items;
    }

    // STATUS, FINAL 공통. 형식 오류면 null
    public static StatusInfo parseStatus(Message m) {
        Map<String, Map<Integer, Integer>> byUser = new LinkedHashMap<>();
        if (!m.field(0).isEmpty()) {
            for (String item : m.field(0).split(",", -1)) {
                String[] p = item.split(":", -1);
                if (p.length != 3) {
                    return null;
                }
                try {
                    byUser.computeIfAbsent(p[0], k -> new LinkedHashMap<>())
                          .put(Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        Map<Integer, Integer> counts = parseOrder(m.field(1));
        if (counts == null) {
            return null;
        }
        try {
            int total = m.field(2).isEmpty() ? 0 : Integer.parseInt(m.field(2));
            return new StatusInfo(byUser, counts, total);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
