package client;

import java.io.*;
import java.util.LinkedHashMap;

/*
 * 공동 주문 취합 시스템 클라이언트의 메인 프로그램 (= 입력 스레드).
 *
 *   1. 서버 주소를 정한다: 실행 인자(IP 포트) -> serverinfo.dat -> 127.0.0.1:5050 순서
 *   2. 서버에 연결한다
 *   3. 닉네임을 입력받는다
 *   4. 수신 스레드(ReceiverThread)를 시작한다
 *   5. 반복:
 *        키보드에서 명령을 읽고, 서버에 요청을 보낸다
 *      (응답은 기다리지 않는다. 응답과 알림은 수신 스레드가 받아서 출력한다)
 *
 * 멀티스레드 / 동기화 정리
 *   - 메인 스레드 : 키보드 입력 -> 서버 요청
 *   - 수신 스레드 : 서버 메시지 -> 화면 출력
 *   - 화면 출력   : ConsoleView 의 synchronized 메서드로만 출력 (글자 섞임 방지)
 *   - 공유 상태   : ClientState 의 synchronized 메서드로만 읽고 씀
 *   - 소켓 전송   : ServerConnection.send() 가 synchronized
 *
 * 클라이언트는 형식(빈 이름, 숫자가 아닌 수량 등)만 미리 확인하고,
 * 방장 권한과 마감 여부는 서버가 확인한다. (서버가 ERR|NOT_HOST, ERR|CLOSED 로 거절)
 */
public class ClientMain {

    private ClientState state = new ClientState();
    private ConsoleView view = new ConsoleView(state);
    private ServerConnection conn;

    public static void main(String argv[]) throws IOException {
        ClientMain client = new ClientMain();
        client.run(argv);
    }

    void run(String argv[]) throws IOException {
        // ---- 1. 서버 주소 정하기 (실행 인자가 있으면 인자, 없으면 serverinfo.dat) ----
        String host = "127.0.0.1";   // default IP
        int port = 5050;             // default port
        try {
            if (argv.length >= 2) {  // README:  java -cp out client.ClientMain 127.0.0.1 5050
                host = argv[0];
                port = Integer.parseInt(argv[1]);
            } else {                 // 인자가 없으면 serverinfo.dat 읽기 (host=..., port=...)
                BufferedReader file = new BufferedReader(new FileReader("serverinfo.dat"));
                String line;
                while ((line = file.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("host=")) {
                        host = line.substring(5).trim();
                    } else if (line.startsWith("port=")) {
                        port = Integer.parseInt(line.substring(5).trim());
                    }
                }
                file.close();
            }
        } catch (Exception e) {      // 파일이 없거나 포트가 숫자가 아님
            System.out.println("서버 주소를 읽을 수 없어 " + host + ":" + port + " 를 사용합니다.");
        }

        // ---- 2. connect to the server ----
        try {
            conn = new ServerConnection(host, port);
        } catch (IOException e) {
            System.out.println("서버에 연결할 수 없습니다: " + host + ":" + port + " (" + e.getMessage() + ")");
            return;
        }
        System.out.println("서버에 연결되었습니다. (" + host + ":" + port + ")");

        // 키보드 입력 통로. 한글이 깨지지 않게 콘솔(명령 창)과 같은 문자 인코딩으로 읽는다
        BufferedReader inFromUser = new BufferedReader(new InputStreamReader(System.in, System.getProperty("stdout.encoding", "UTF-8")));

        // ---- 3. 닉네임 입력 ----
        while (true) {
            System.out.print("닉네임을 입력하세요: ");
            String nick = inFromUser.readLine();
            if (nick == null) {          // keyboard input closed
                conn.close();
                return;
            }
            nick = nick.trim();
            if (ClientProtocol.isValidName(nick)) {
                state.setNickname(nick);
                break;
            }
            System.out.println("닉네임은 비워 둘 수 없고 | , : 를 쓸 수 없습니다.");
        }

        // ---- 4. 수신 스레드 시작 ----
        ReceiverThread receiver = new ReceiverThread(conn, state, view);
        // 데몬 스레드: 메인 스레드(입력)가 끝나면 이 스레드도 같이 끝난다
        receiver.setDaemon(true);
        receiver.start();

        view.showLines(view.helpLines());
        state.requestRoomList();
        conn.send(ClientProtocol.rooms());      // 처음 방 목록 요청

        // ---- 5. command loop ----
        while (state.isConnected()) {
            String sentence = inFromUser.readLine();
            if (sentence == null) {
                break;                          // keyboard input closed
            }
            if (!state.isConnected()) {
                break;                          // 그 사이 서버 연결이 끊김
            }
            sentence = sentence.trim();
            if (sentence.equals("")) {
                view.prompt();                  // ignore empty input
                continue;
            }
            String command = menuToCommand(sentence);   // 번호면 명령어로 바꾼다 (질문만 했으면 null)
            if (command != null && !handleCommand(command)) {
                break;                          // quit
            }
        }

        conn.close();
        System.out.println("프로그램을 종료합니다.");
    }

    /*
     * 명령 하나를 처리한다.
     * 예) "order 1:2,3:1"  ->  cmd = "order",  arg = "1:2,3:1"
     * false 를 돌려주면 프로그램을 끝낸다. (quit)
     */
    boolean handleCommand(String sentence) {
        String[] parts = sentence.split("\\s+", 2);
        String cmd = parts[0].toLowerCase();
        String arg = "";
        if (parts.length > 1) {
            arg = parts[1].trim();
        }
        ClientState.Mode mode = state.getMode();

        // ---------------- 공통 ----------------
        if (cmd.equals("help")) {
            view.showLines(view.helpLines());

        } else if (cmd.equals("quit")) {
            state.setQuitting();
            if (mode != ClientState.Mode.LOBBY) {
                conn.send(ClientProtocol.leave());
            }
            return false;

        // ---------------- 로비 ----------------
        } else if (cmd.equals("list")) {
            if (mode != ClientState.Mode.LOBBY) {
                view.show("로비에서만 쓸 수 있습니다.");
            } else {
                state.requestRoomList();
                conn.send(ClientProtocol.rooms());
            }

        } else if (cmd.equals("create")) {
            if (mode != ClientState.Mode.LOBBY) {
                view.show("이미 방에 있습니다. 먼저 leave 하세요.");
            } else if (!ClientProtocol.isValidName(arg)) {
                view.show("사용법: create <방이름>  (| , : 는 쓸 수 없음)");
            } else {
                conn.send(ClientProtocol.create(state.getNickname(), arg));
            }

        } else if (cmd.equals("join")) {
            if (mode != ClientState.Mode.LOBBY) {
                view.show("이미 방에 있습니다. 먼저 leave 하세요.");
            } else {
                try {
                    int id = Integer.parseInt(arg);
                    state.setPendingJoin(id);   // 응답(OK|JOIN)이 오면 이 방으로 들어간 것으로 처리
                    conn.send(ClientProtocol.join(id, state.getNickname()));
                } catch (NumberFormatException e) {
                    view.show("사용법: join <방번호>  (예: join 1)");
                }
            }

        } else if (cmd.equals("nick")) {
            // 닉네임은 CREATE / JOIN 을 보낼 때마다 같이 보내므로, 로비에서는 메모장(state)만 바꾸면 된다
            if (mode != ClientState.Mode.LOBBY) {
                view.show("닉네임은 로비에서만 바꿀 수 있습니다. 먼저 leave 하세요.");
            } else if (!ClientProtocol.isValidName(arg)) {
                view.show("사용법: nick <새 닉네임>  (비워 둘 수 없고 | , : 는 쓸 수 없음)");
            } else {
                state.setNickname(arg);
                view.show("닉네임을 '" + arg + "'(으)로 바꿨습니다.");   // 프롬프트에도 바로 반영된다
            }

        // ---------------- 방 안 ----------------
        } else if (cmd.equals("menu")) {
            if (mode == ClientState.Mode.LOBBY) {
                view.show("방에 들어가야 메뉴를 볼 수 있습니다.");
            } else {
                view.showLines(view.menuLines());
            }

        } else if (cmd.equals("order")) {
            // 마감 여부는 서버가 확인한다 (마감 후 주문 -> 서버가 ERR|CLOSED)
            if (mode == ClientState.Mode.LOBBY) {
                view.show("방에 들어가야 주문할 수 있습니다.");
            } else {
                sendOrder(arg);
            }

        } else if (cmd.equals("cancel")) {
            if (mode == ClientState.Mode.LOBBY) {
                view.show("방에 들어가 있지 않습니다.");
            } else {
                conn.send(ClientProtocol.cancel());
            }

        } else if (cmd.equals("clear")) {
            // 방장인지는 서버가 확인한다 (참여자가 보내면 서버가 ERR|NOT_HOST)
            if (mode == ClientState.Mode.LOBBY) {
                view.show("방에 들어가 있지 않습니다.");
            } else {
                conn.send(ClientProtocol.clear());
            }

        } else if (cmd.equals("close")) {
            if (mode == ClientState.Mode.LOBBY) {
                view.show("방에 들어가 있지 않습니다.");
            } else {
                conn.send(ClientProtocol.close());
            }

        } else if (cmd.equals("leave")) {
            if (mode == ClientState.Mode.LOBBY) {
                view.show("이미 로비에 있습니다.");
            } else {
                conn.send(ClientProtocol.leave());
                state.toLobby();
                view.show("방에서 나왔습니다.");
                state.requestRoomList();
                conn.send(ClientProtocol.rooms());
            }

        } else {
            view.show("알 수 없는 명령입니다. help 를 입력하세요.");
        }
        return true;
    }

    // 주문 입력을 확인하고 ORDER 메시지를 보낸다
    private void sendOrder(String arg) {
        LinkedHashMap<Integer, Integer> items;
        try {
            items = ClientProtocol.parseOrderInput(arg);
        } catch (IllegalArgumentException e) {
            view.show("[입력 오류] " + e.getMessage() + "\n예) order 1:2,3:1  또는  order 1 2 3 1");
            return;
        }

        // 메뉴판을 받았다면 없는 메뉴는 미리 걸러 준다 (서버도 다시 확인한다)
        if (state.hasMenu()) {
            for (Integer id : items.keySet()) {
                if (!state.hasMenuItem(id)) {
                    view.show("[입력 오류] " + id + "번 메뉴는 없습니다. menu 로 확인하세요.");
                    return;
                }
            }
        }
        conn.send(ClientProtocol.order(items));
    }

    /*
     * 번호 메뉴 (앱의 버튼처럼 번호로 고르기)
     *   - 질문에 대한 답이면: 명령어를 완성한다.   예) 질문 "create" + 답 "치킨방" -> "create 치킨방"
     *   - 번호면: 지금 화면에 맞는 명령어로 바꾼다. 예) 로비에서 "1" -> "list"
     *             더 받아야 할 값이 있으면 질문만 하고 null 을 돌려준다
     *   - 그 밖(create 치킨방 같은 명령어)은 그대로 돌려준다.
     * 결국 번호를 누르든 명령어를 치든 handleCommand 의 같은 코드가 실행된다.
     */
    private String menuToCommand(String sentence) {
        String asked = state.takeQuestion();
        if (asked != null) {                    // 질문에 대한 답
            if (sentence.equals("취소")) {
                view.show("취소했습니다.");
                return null;
            }
            return asked + " " + sentence;
        }

        if (state.getMode() == ClientState.Mode.LOBBY) {
            switch (sentence) {
                case "1": return "list";
                case "2": return ask("create", "만들 방 이름을 입력하세요.");
                case "3":
                    state.requestRoomList();
                    conn.send(ClientProtocol.rooms());      // 방 목록을 먼저 보여 준다
                    return ask("join", "목록에서 들어갈 방 번호를 입력하세요.");
                case "4": return ask("nick", "새 닉네임을 입력하세요.");
                case "0": return "quit";
            }
        } else {                                // 방 안 (마감된 방 포함)
            switch (sentence) {
                case "1": return "menu";
                case "2":
                    view.showLines(view.menuLines());       // 메뉴판을 보여 주고 주문을 묻는다
                    return ask("order", "메뉴번호:수량 으로 입력하세요.  예) 1:2,3:1");
                case "3": return "cancel";
                case "4": return "leave";
                case "5": return "clear";
                case "6": return "close";
                case "0": return "quit";
            }
        }
        return sentence;                        // 번호가 아니면 입력한 명령어 그대로
    }

    // 질문을 메모장에 적고 안내를 보여 준다. 그러면 프롬프트가 질문으로 바뀐다
    private String ask(String command, String guide) {
        state.setQuestion(command);
        view.show(guide + "  (그만두려면 '취소' 라고 입력하세요)");
        return null;
    }
}
