package client;

import java.io.*;

/*
 * 수신 스레드.
 *
 * 요청 하나에 응답 하나만 오는 프로그램이면 "보내고 -> 응답을 기다리고 -> 다시 입력" 을 한 스레드로 할 수 있다.
 * 그런데 이 프로젝트는 내가 입력하지 않아도 서버가 먼저 알림을 보낸다.
 *   예) 같은 방의 다른 사람이 주문을 바꾸면 STATUS 가 온다.
 * 메인 스레드는 키보드 입력(readLine)을 기다리느라 멈춰 있으므로,
 * 서버 메시지는 이 스레드가 따로 계속 받아서 화면에 보여 준다.
 *
 * 서버 메시지 한 줄을 받으면 첫 단어(명령)를 보고 처리한다.
 *   OK, ERR, ROOM_LIST, MENU, STATUS, FINAL, ROOM_END, PING
 */
class ReceiverThread extends Thread {

    private ServerConnection conn;
    private ClientState state;
    private ConsoleView view;

    ReceiverThread(ServerConnection conn, ClientState state, ConsoleView view) {
        this.conn = conn;
        this.state = state;
        this.view = view;
    }

    public void run() {
        try {
            String line = conn.readLine();
            while (line != null) {              // null = 서버가 연결을 끊음
                if (!line.equals("")) {
                    handle(line);
                }
                line = conn.readLine();
            }
        } catch (IOException e) {
            // 연결이 끊김 -> 아래에서 처리
        }

        state.setDisconnected();
        // 사용자가 quit 으로 직접 끝낸 경우에는 메시지를 띄우지 않는다
        if (!state.isQuitting()) {
            view.show("서버와 연결이 끊어졌습니다. 엔터를 누르면 종료합니다.");
        }
    }

    // 서버 메시지 한 줄 처리
    private void handle(String line) {
        String[] f = ClientProtocol.split(line);
        String type = f[0];

        switch (type) {
            case "OK":
                handleOk(f);
                break;

            case "ERR":         // ERR|코드
                state.takePendingJoin();   // JOIN 이 실패했을 수도 있으니 대기 상태를 비움
                view.show("[오류] " + view.errorText(field(f, 1)));
                break;

            case "ROOM_LIST":   // 방 목록: 로비에 있을 때만 보여 준다
                if (state.getMode() == ClientState.Mode.LOBBY
                        && state.shouldShowRoomList(field(f, 1))) {
                    view.showLines(view.roomListLines(field(f, 1)));
                }
                break;

            case "MENU":        // 메뉴판: 저장하고 보여 준다
                state.setMenu(view.parseMenu(field(f, 1)));
                view.showLines(view.menuLines());
                break;

            case "STATUS":      // 같은 방 누군가 주문을 바꿀 때마다 온다
                view.showLines(view.statusLines(state.getRoomId() + "번 방 현재 주문 현황",
                        field(f, 1), field(f, 2), field(f, 3)));
                break;

            case "FINAL":       // 방장이 마감했다
                state.markClosed();
                view.showLines(view.statusLines("최종 주문표 (마감)",
                        field(f, 1), field(f, 2), field(f, 3)));
                break;

            case "ROOM_END":    // 방장이 나가서 방이 없어졌다
                state.toLobby();
                view.show("방장이 나가서 방이 종료되었습니다. 로비로 돌아갑니다.");
                state.requestRoomList();
                conn.send(ClientProtocol.rooms());     // 로비 방 목록을 새로 받는다
                break;

            case "PING":        // (확장) 서버의 연결 확인 -> 바로 PONG 응답
                conn.send(ClientProtocol.pong());
                break;

            default:
                view.show("(알 수 없는 메시지) " + line);
        }
    }

    // OK|명령|...
    private void handleOk(String[] f) {
        String cmd = field(f, 1);

        if (cmd.equals("CREATE")) {             // OK|CREATE|방번호|토큰
            int id = toInt(field(f, 2));
            state.enterRoom(id, true, field(f, 3));
            view.show("방을 만들었습니다. (" + id + "번 방, 내가 방장)\n"
                    + "help 를 입력하면 번호 메뉴를 볼 수 있습니다.");

        } else if (cmd.equals("JOIN")) {        // OK|JOIN|토큰
            int id = state.takePendingJoin();
            state.enterRoom(id, false, field(f, 2));
            view.show(id + "번 방에 들어왔습니다. help 를 입력하면 번호 메뉴를 볼 수 있습니다.");

        } else if (cmd.equals("LEAVE")) {
            // 입력 스레드가 leave 를 보낼 때 이미 로비로 바꿔 두므로 보통은 아무것도 안 한다
            if (state.getMode() != ClientState.Mode.LOBBY) {
                state.toLobby();
                view.show("방에서 나왔습니다.");
            }

        } else if (cmd.equals("RECONNECT")) {
            view.show("재접속했습니다. 주문 현황을 다시 불러옵니다.");
        } else if (cmd.equals("ORDER")) {
            view.show("주문이 반영되었습니다.");
        } else if (cmd.equals("CANCEL")) {
            view.show("내 주문을 취소했습니다.");
        } else if (cmd.equals("CLEAR")) {
            view.show("방 전체 주문을 초기화했습니다.");
        } else if (cmd.equals("CLOSE")) {
            view.show("주문을 마감했습니다.");
        } else {
            view.show("요청이 처리되었습니다. (" + cmd + ")");
        }
    }

    // ---------- 작은 도우미 ----------

    // f[i] 를 꺼낸다. 없으면 "" (배열 범위를 넘지 않게)
    private static String field(String[] f, int i) {
        if (i < f.length) {
            return f[i];
        }
        return "";
    }

    private static int toInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
