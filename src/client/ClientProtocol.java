package client;

import java.util.LinkedHashMap;

/*
 * 우리 팀 프로토콜에서 클라이언트가 쓰는 기능 모음.
 * (메시지를 만들고 자르는 일은 모두 이 클래스에서 한다)
 *
 * 메시지 형식:  명령|필드1|필드2 ...   (한 줄 = 메시지 하나)
 *   예) ORDER|1:2,3:1    -> 1번 메뉴 2개, 3번 메뉴 1개 주문
 *
 * ※ 5번(프로토콜 담당)의 공통 파서가 나오면
 *    이 클래스의 메서드 안쪽만 그 파서를 부르도록 바꾸면 된다.
 */
class ClientProtocol {

    // ---------- 받은 메시지 자르기 ----------

    // "명령|필드1|필드2" -> ["명령", "필드1", "필드2"]
    // split 의 두 번째 값 -1 : 끝에 있는 빈 필드도 버리지 않는다
    //   예) "STATUS|||0" -> ["STATUS", "", "", "0"]
    static String[] split(String line) {
        return line.split("\\|", -1);
    }

    // 닉네임·방이름 검사
    // 비어 있으면 안 되고, 구분 문자 | , : 가 들어가면 안 된다
    static boolean isValidName(String s) {
        if (s == null || s.trim().equals("")) {
            return false;
        }
        if (s.contains("|") || s.contains(",") || s.contains(":")) {
            return false;
        }
        return true;
    }

    // ---------- 보낼 메시지 만들기 ----------

    static String rooms()                          { return "ROOMS"; }
    static String create(String nick, String room) { return "CREATE|" + nick + "|" + room; }
    static String join(int roomId, String nick)    { return "JOIN|" + roomId + "|" + nick; }
    static String cancel()                         { return "CANCEL"; }
    static String clear()                          { return "CLEAR"; }
    static String close()                          { return "CLOSE"; }
    static String leave()                          { return "LEAVE"; }
    static String reconnect(String token)          { return "RECONNECT|" + token; }
    static String pong()                           { return "PONG"; }

    // {1=2, 3=1} -> "ORDER|1:2,3:1"
    // (보낼 때마다 내 주문 전체가 이 내용으로 바뀐다)
    static String order(LinkedHashMap<Integer, Integer> items) {
        String msg = "ORDER|";
        boolean first = true;
        for (Integer id : items.keySet()) {
            if (!first) {
                msg = msg + ",";
            }
            msg = msg + id + ":" + items.get(id);
            first = false;
        }
        return msg;
    }

    /*
     * 사용자가 입력한 주문을 {메뉴번호=수량} 으로 바꾼다.
     * 두 가지 입력 형식을 모두 받는다.
     *    "1:2,3:1"   또는   "1 2 3 1"  (메뉴번호 수량 메뉴번호 수량 ...)
     * 형식이 틀리면 IllegalArgumentException 을 던진다. (메시지 = 사용자에게 보여 줄 문장)
     */
    static LinkedHashMap<Integer, Integer> parseOrderInput(String text) {
        LinkedHashMap<Integer, Integer> items = new LinkedHashMap<Integer, Integer>();
        String t = text.trim();
        if (t.equals("")) {
            throw new IllegalArgumentException("주문 내용이 비어 있습니다.");
        }

        if (t.contains(":")) {
            // 형식 1: "1:2,3:1"
            String[] parts = t.split(",");
            for (int i = 0; i < parts.length; i++) {
                String[] p = parts[i].trim().split(":");
                if (p.length != 2) {
                    throw new IllegalArgumentException("형식: 메뉴번호:수량,메뉴번호:수량");
                }
                addItem(items, p[0], p[1]);
            }
        } else {
            // 형식 2: "1 2 3 1"
            String[] p = t.split("\\s+");
            if (p.length % 2 != 0) {
                throw new IllegalArgumentException("메뉴번호와 수량을 짝지어 입력하세요.");
            }
            for (int i = 0; i < p.length; i += 2) {
                addItem(items, p[i], p[i + 1]);
            }
        }
        return items;
    }

    // 메뉴 하나를 주문 목록에 넣는다. 같은 메뉴를 두 번 적으면 수량을 더한다.
    private static void addItem(LinkedHashMap<Integer, Integer> items, String idText, String qtyText) {
        int id;
        int qty;
        try {
            id = Integer.parseInt(idText.trim());
            qty = Integer.parseInt(qtyText.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("메뉴번호와 수량은 숫자로 입력하세요.");
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다.");
        }

        if (items.containsKey(id)) {
            items.put(id, items.get(id) + qty);
        } else {
            items.put(id, qty);
        }
    }
}
