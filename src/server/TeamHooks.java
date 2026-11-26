package server;

import common.Message;

/**
 * 2번(주문·마감)과 3번(연결·세션) 코드에 연결되는 자리를 한곳에 모은 클래스.
 * 담당자와 확인이 끝나면 이 파일 안쪽만 바꾸면 되고, 방 관리·요청 분배 코드는 고치지 않아도 된다.
 * null을 돌려주는 메서드는 "아직 보낼 내용이 없음"을 뜻하며, 그 메시지는 보내지 않는다.
 */
final class TeamHooks {

    private final TokenManager tokens = new TokenManager();  // 3번 (dev에 있음)

    // ===== 3번: 연결·세션 =====

    /**
     * CREATE·JOIN 성공 때 재접속 토큰을 발급한다 (3번 TokenManager).
     * 세션 생성(SessionManager.createSession) 연결은 3번 확인 후 추가한다.
     */
    String issueToken(ClientHandler client, Room room, String nickname) {
        return tokens.generateToken();
    }

    /** RECONNECT|토큰. 3번 ReconnectManager로 넘긴다 (성공 응답 순서·연결 교체 방식 확인 후). 지금은 응답하지 않는다. */
    void reconnect(ClientHandler client, String token) {
        Log.info("RECONNECT 처리는 3번 코드 연결 후 동작 (지금은 응답 없음)");
    }

    /** PONG. 3번의 연결 확인으로 넘긴다 (PING 사용 여부 확인 후). */
    void pong(ClientHandler client) {
    }

    // ===== 2번: 주문·마감 =====

    /** CREATE·JOIN 뒤 보낼 MENU 한 줄. 2번의 메뉴판이 나오면 Protocol.menu(...)로 만든다. */
    String menuLine() {
        return null;
    }

    /** 이 방의 현재 STATUS 한 줄 (방 락 안에서 불린다). 2번의 집계가 나오면 Protocol.status(...)로 만든다. */
    String statusLine(Room room) {
        return null;
    }

    /** 멤버가 나갔을 때 (방 락 안). 2번: 마감 전이면 그 사람 주문 삭제. */
    void onMemberLeft(Room room, String nickname) {
    }

    /** 방장이 나가 방이 없어졌을 때 (방 락 안). 2번: 방 주문 정리, 3번: 세션 정리. */
    void onRoomEnded(Room room) {
    }

    /**
     * ORDER·CANCEL·CLEAR·CLOSE (방 안에 있는지는 확인이 끝난 상태).
     * 2번 메서드로 넘긴다. NOT_HOST·CLOSED·INVALID_MENU 검사와 STATUS·FINAL 전송도 2번 쪽 (확인 대기).
     */
    void orderCommand(ClientHandler client, Room room, Message message) {
        Log.info(message.command + " 처리는 2번 코드 연결 후 동작 (지금은 응답 없음)");
    }
}
