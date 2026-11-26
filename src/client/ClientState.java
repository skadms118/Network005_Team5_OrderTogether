package client;

import java.util.LinkedHashMap;

/*
 * 클라이언트의 현재 상태를 저장하는 클래스.
 *   - 지금 화면 (로비 / 방 안 / 마감된 방)
 *   - 들어가 있는 방 번호, 방장인지 여부
 *   - 메뉴판
 *
 * 입력 스레드(ClientMain)와 수신 스레드(ReceiverThread)가
 * 이 객체를 같이 읽고 쓰기 때문에 모든 메서드에 synchronized 를 붙였다.
 * (한 스레드가 고치는 도중에 다른 스레드가 읽지 못하게 하려고)
 */
class ClientState {

    // 지금 사용자가 있는 화면
    enum Mode { LOBBY, ROOM, CLOSED }

    private String nickname;
    private Mode mode = Mode.LOBBY;
    private int roomId = -1;            // 들어가 있는 방 번호 (로비면 -1)
    private boolean host = false;       // 방장이면 true
    private String token = null;        // 재접속용 토큰 (확장 기능)
    private int pendingJoinId = -1;     // JOIN 을 보내고 응답을 기다리는 방 번호
    private boolean connected = true;   // 서버와 연결되어 있는지
    private boolean quitting = false;   // 사용자가 quit 으로 직접 끝내는 중인지
    private String lastRoomList = null; // 마지막으로 화면에 보여 준 방 목록
    private boolean listRequested = false;  // 사용자가 방 목록을 직접 요청했는지

    // 메뉴판 (메뉴 번호 -> 메뉴). LinkedHashMap 은 넣은 순서를 기억한다.
    private LinkedHashMap<Integer, MenuItem> menu = new LinkedHashMap<Integer, MenuItem>();

    // ---------- 닉네임 ----------
    synchronized void setNickname(String n) { nickname = n; }
    synchronized String getNickname()        { return nickname; }

    // ---------- 화면 상태 ----------
    synchronized Mode getMode()     { return mode; }
    synchronized int getRoomId()    { return roomId; }
    synchronized boolean isHost()   { return host; }
    synchronized String getToken()  { return token; }

    // 방에 들어감 (서버가 OK|CREATE 또는 OK|JOIN 을 보냈을 때)
    synchronized void enterRoom(int id, boolean isHost, String tk) {
        mode = Mode.ROOM;
        roomId = id;
        host = isHost;
        if (tk != null && !tk.equals("")) {
            token = tk;
        }
        pendingJoinId = -1;
        lastRoomList = null;
    }

    // 마감됨 (서버가 FINAL 을 보냈을 때)
    synchronized void markClosed() {
        if (mode == Mode.ROOM) {
            mode = Mode.CLOSED;
        }
    }

    // 로비로 돌아감 (leave 했거나, 서버가 ROOM_END 를 보냈을 때)
    synchronized void toLobby() {
        mode = Mode.LOBBY;
        roomId = -1;
        host = false;
        token = null;
        pendingJoinId = -1;
        lastRoomList = null;
        menu.clear();
    }

    // ---------- JOIN 응답 대기 ----------
    synchronized void setPendingJoin(int id) { pendingJoinId = id; }

    // 기다리던 방 번호를 꺼내고 비운다
    synchronized int takePendingJoin() {
        int id = pendingJoinId;
        pendingJoinId = -1;
        return id;
    }

    // ---------- 연결 / 종료 ----------
    synchronized boolean isConnected()  { return connected; }
    synchronized void setDisconnected() { connected = false; }
    synchronized void setQuitting()     { quitting = true; }
    synchronized boolean isQuitting()   { return quitting; }

    // ---------- 방 목록이 두 번 출력되지 않게 ----------
    // 사용자가 list 처럼 직접 요청했으면 다음 목록은 무조건 보여 준다
    synchronized void requestRoomList() { listRequested = true; }

    // 서버가 자동으로 보낸 목록이 바로 전에 보여 준 것과 같으면 다시 출력하지 않는다
    synchronized boolean shouldShowRoomList(String payload) {
        boolean show = false;
        if (listRequested) {
            show = true;
        } else if (!payload.equals(lastRoomList)) {
            show = true;
        }
        listRequested = false;
        lastRoomList = payload;
        return show;
    }

    // ---------- 메뉴판 ----------
    synchronized void setMenu(LinkedHashMap<Integer, MenuItem> m) {
        menu.clear();
        menu.putAll(m);
    }

    synchronized boolean hasMenu()            { return !menu.isEmpty(); }
    synchronized boolean hasMenuItem(int id)  { return menu.containsKey(id); }

    // 다른 스레드가 메뉴판을 바꿔도 안전하도록 복사본을 돌려준다
    synchronized LinkedHashMap<Integer, MenuItem> menuCopy() {
        return new LinkedHashMap<Integer, MenuItem>(menu);
    }

    // ---- 번호 메뉴: 지금 사용자에게 묻고 있는 질문 ----
    // 예) 로비에서 2(방 만들기)를 고르면 question = "create" 가 되고, 다음 입력을 방 이름으로 쓴다
    private String question = null;

    synchronized void setQuestion(String q) { question = q; }
    synchronized String getQuestion()       { return question; }

    // 질문을 꺼내면서 동시에 비운다 (takePendingJoin 과 같은 방식)
    synchronized String takeQuestion() {
        String q = question;
        question = null;
        return q;
    }
}
