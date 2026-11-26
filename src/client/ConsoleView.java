package client;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/*
 * 화면 출력 담당.
 *
 * 입력 스레드와 수신 스레드가 동시에 System.out 에 쓰면 글자가 섞인다.
 * 그래서 화면 출력은 반드시 이 클래스의 synchronized 메서드
 * (show, showLines, prompt) 로만 한다.
 *
 * 알림을 출력한 뒤에는 입력 프롬프트("> ")를 다시 그려서
 * 사용자가 어디에 입력하면 되는지 알 수 있게 한다.
 */
class ConsoleView {

    static final String LINE = "------------------------------------------";

    private ClientState state;

    ConsoleView(ClientState state) {
        this.state = state;
    }

    // ================= 출력 (synchronized) =================

    // 문장 하나 출력 (문장 안에 "\n" 이 있으면 여러 줄) + 프롬프트 다시 그리기
    synchronized void show(String text) {
        System.out.println();
        System.out.println(text);
        System.out.print(promptText());
        System.out.flush();   // 줄바꿈이 없는 프롬프트도 바로 보이게
    }

    // 여러 줄 출력 + 프롬프트 다시 그리기
    synchronized void showLines(ArrayList<String> lines) {
        System.out.println();
        for (int i = 0; i < lines.size(); i++) {
            System.out.println(lines.get(i));
        }
        System.out.print(promptText());
        System.out.flush();   // 줄바꿈이 없는 프롬프트도 바로 보이게
    }

    // 프롬프트만 출력
    synchronized void prompt() {
        System.out.print(promptText());
        System.out.flush();   // 줄바꿈이 없는 프롬프트도 바로 보이게
    }

    // 지금 화면에 맞는 프롬프트
    //   [로비] 철수>   /   [1번 방 · 방장] 철수>   /   [1번 방 · 마감] 철수>
    private String promptText() {
        String q = state.getQuestion();          // 번호 메뉴에서 묻는 중이면 그 질문을 프롬프트로
        if (q != null) {
            return questionText(q);
        }
        String nick = state.getNickname() == null ? "" : state.getNickname();
        if (state.getMode() == ClientState.Mode.ROOM) {
            String text = "[" + state.getRoomId() + "번 방";
            if (state.isHost()) {
                text = text + " · 방장";
            }
            return text + "] " + nick + "> ";
        } else if (state.getMode() == ClientState.Mode.CLOSED) {
            return "[" + state.getRoomId() + "번 방 · 마감] " + nick + "> ";
        } else {
            return "[로비] " + nick + "> ";
        }
    }

    // ================= 도움말 =================
    ArrayList<String> helpLines() {
        ArrayList<String> l = new ArrayList<String>();
        switch (state.getMode()) {
            case LOBBY:
                l.add("[로비 메뉴]  번호를 누르세요");
                l.add("  1) 방 목록 보기");
                l.add("  2) 방 만들기        (방 이름은 다음에 물어봐요)");
                l.add("  3) 방 들어가기      (방 목록을 보여 주고 방 번호를 물어봐요)");
                l.add("  4) 닉네임 바꾸기    (새 이름은 다음에 물어봐요)");
                l.add("  0) 종료");
                break;
            case ROOM:
                l.add("[방 메뉴]  번호를 누르세요");
                l.add("  1) 메뉴판 보기");
                l.add("  2) 주문하기          (메뉴판을 보여 주고 주문을 물어봐요)");
                l.add("                       주문은 1:2,3:1 처럼  (1번 메뉴 2개, 3번 메뉴 1개)");
                l.add("                       ※ 다시 주문하면 내 주문 전체가 바뀜");
                l.add("  3) 내 주문 취소");
                l.add("  4) 방 나가기");
                if (state.isHost()) {
                    l.add("  5) [방장] 전체 주문 초기화");
                    l.add("  6) [방장] 주문 마감");
                }
                l.add("  0) 종료");
                break;
            case CLOSED:
                l.add("[마감된 방]  번호를 누르세요");
                l.add("  4) 방 나가기 (로비로)");
                l.add("  0) 종료");
                break;
        }
        l.add("  help = 메뉴 다시 보기   (익숙해지면 명령어로 바로 쳐도 돼요. 예: create 치킨방, order 1:2)");
        return l;
    }

    // ================= 서버 메시지 -> 화면 문장 =================

    // ROOM_LIST 내용  "1:치킨방:3/5:OPEN,2:피자방:5/5:CLOSED"
    ArrayList<String> roomListLines(String payload) {
        ArrayList<String> l = new ArrayList<String>();
        l.add(LINE);
        l.add(" 방 목록");
        l.add(LINE);
        if (payload.equals("")) {
            l.add(" (아직 방이 없습니다. 2번(방 만들기)으로 만들어 보세요)");
        } else {
            String[] rooms = payload.split(",");
            for (int i = 0; i < rooms.length; i++) {
                String[] p = rooms[i].split(":");   // [번호, 이름, 인원, 상태]
                if (p.length < 4) {
                    l.add(" " + rooms[i]);
                    continue;
                }
                String st = "마감";
                if (p[3].equals("OPEN")) {
                    st = "주문 중";
                }
                l.add(" #" + p[0] + "  " + p[1] + "  (" + p[2] + "명)  " + st);
            }
        }
        l.add(LINE);
        return l;
    }

    // MENU 내용  "1:후라이드:18000,2:양념:19000"  ->  {1=후라이드, 2=양념}
    LinkedHashMap<Integer, MenuItem> parseMenu(String payload) {
        LinkedHashMap<Integer, MenuItem> m = new LinkedHashMap<Integer, MenuItem>();
        if (payload.equals("")) {
            return m;
        }
        String[] items = payload.split(",");
        for (int i = 0; i < items.length; i++) {
            String[] p = items[i].split(":");       // [번호, 이름, 가격]
            if (p.length < 3) {
                continue;
            }
            try {
                int id = Integer.parseInt(p[0]);
                int price = Integer.parseInt(p[2]);
                m.put(id, new MenuItem(id, p[1], price));
            } catch (NumberFormatException e) {
                // 숫자가 아니면 그 메뉴는 건너뛴다
            }
        }
        return m;
    }

    ArrayList<String> menuLines() {
        ArrayList<String> l = new ArrayList<String>();
        l.add(LINE);
        l.add(" 메뉴판");
        l.add(LINE);
        LinkedHashMap<Integer, MenuItem> menu = state.menuCopy();
        if (menu.isEmpty()) {
            l.add(" (메뉴 정보를 아직 받지 못했습니다)");
        } else {
            for (Integer id : menu.keySet()) {
                MenuItem it = menu.get(id);
                l.add(" " + it.id + ". " + it.name + "  " + won(it.price) + "원");
            }
        }
        l.add(LINE);
        return l;
    }

    /*
     * STATUS / FINAL 내용 (필드 3개)
     *   people = "철수:1:2,영희:3:1"   (사람:메뉴번호:수량)
     *   counts = "1:2,3:1"             (메뉴번호:수량)
     *   total  = "55000"               (총금액)
     */
    ArrayList<String> statusLines(String title, String people, String counts, String total) {
        LinkedHashMap<Integer, MenuItem> menu = state.menuCopy();
        ArrayList<String> l = new ArrayList<String>();
        l.add(LINE);
        l.add(" " + title);
        l.add(LINE);

        if (people.equals("")) {
            l.add(" (아직 주문이 없습니다)");
        } else {
            // 1) 사람별로 묶기:  철수 -> [후라이드 x2],  영희 -> [간장 x1]
            LinkedHashMap<String, ArrayList<String>> byPerson = new LinkedHashMap<String, ArrayList<String>>();
            String[] entries = people.split(",");
            for (int i = 0; i < entries.length; i++) {
                String[] p = entries[i].split(":");  // [사람, 메뉴번호, 수량]
                if (p.length < 3) {
                    continue;
                }
                if (!byPerson.containsKey(p[0])) {
                    byPerson.put(p[0], new ArrayList<String>());
                }
                byPerson.get(p[0]).add(menuName(menu, p[1]) + " x" + p[2]);
            }
            for (String person : byPerson.keySet()) {
                l.add(" " + person + " : " + joinWithComma(byPerson.get(person)));
            }
            l.add(LINE);

            // 2) 메뉴별 합계:  후라이드 2개, 간장 1개
            ArrayList<String> c = new ArrayList<String>();
            if (!counts.equals("")) {
                String[] cs = counts.split(",");
                for (int i = 0; i < cs.length; i++) {
                    String[] p = cs[i].split(":");   // [메뉴번호, 수량]
                    if (p.length == 2) {
                        c.add(menuName(menu, p[0]) + " " + p[1] + "개");
                    }
                }
            }
            l.add(" 메뉴별 : " + joinWithComma(c));
        }
        l.add(" 총금액 : " + won(toInt(total)) + "원");
        l.add(LINE);
        return l;
    }

    // 에러 코드 -> 사용자에게 보여 줄 문장
    String errorText(String code) {
        switch (code) {
            case "ALREADY_IN_ROOM": return "이미 방에 들어가 있습니다. 먼저 leave 하세요.";
            case "NOT_IN_ROOM":     return "방에 들어가 있지 않습니다.";
            case "NO_ROOM":         return "없는 방입니다. list 로 방 목록을 확인하세요.";
            case "ROOM_FULL":       return "방 인원(5명)이 가득 찼습니다.";
            case "DUP_NICK":        return "그 방에 같은 닉네임이 이미 있습니다.";
            case "INVALID_NAME":    return "이름이 비어 있거나 | , : 가 들어 있습니다.";
            case "INVALID_MENU":    return "없는 메뉴이거나 수량이 잘못되었습니다.";
            case "NOT_HOST":        return "방장만 할 수 있는 기능입니다.";
            case "CLOSED":          return "이미 마감된 주문입니다.";
            case "INVALID_TOKEN":   return "재접속 정보가 만료되었습니다.";
            default:                return "요청이 거절되었습니다. (" + code + ")";
        }
    }

    // ================= 작은 도우미 =================

    // 메뉴 번호 -> 메뉴 이름 (메뉴판에 없으면 "메뉴3" 처럼 표시)
    private static String menuName(LinkedHashMap<Integer, MenuItem> menu, String idText) {
        int id = toInt(idText);
        if (menu.containsKey(id)) {
            return menu.get(id).name;
        }
        return "메뉴" + idText;
    }

    // ["후라이드 x2", "콜라 x1"] -> "후라이드 x2, 콜라 x1"
    private static String joinWithComma(ArrayList<String> list) {
        String result = "";
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                result = result + ", ";
            }
            result = result + list.get(i);
        }
        return result;
    }

    // 문자열 -> 정수 (숫자가 아니면 0)
    private static int toInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 36000 -> "36,000"  (%,d 는 세 자리마다 쉼표를 넣는 형식)
    private static String won(int n) {
        return String.format("%,d", n);
    }

    // 번호 메뉴에서 질문 중일 때 보여 줄 프롬프트
    private String questionText(String q) {
        switch (q) {
            case "create": return "만들 방 이름> ";
            case "join":   return "들어갈 방 번호> ";
            case "nick":   return "새 닉네임> ";
            case "order":  return "주문 (예: 1:2,3:1)> ";
            default:       return q + "> ";
        }
    }
}
