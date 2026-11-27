package server;

import common.RoomInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 주문방 하나. 방 번호, 이름, 방장, 참가자(최대 5명), 마감 여부를 가진다.
 *
 * 방 락: 이 Room 객체 자체를 락으로 쓴다 (synchronized 메서드, synchronized (room) 블록).
 *   - 입장 때 정원·닉네임 검사와 추가를 이 락 안에서 한 번에 처리한다 (⑤ 동기화)
 *   - 2번의 주문 변경·마감도 같은 락을 쓰는 것으로 제안함 (2번 확인 대기)
 *   - 이 락을 잡은 채로 다른 방의 락을 잡거나 로비 전송을 하지 않는다 (락 겹침 방지)
 */
final class Room {

    static final int MAX_MEMBERS = 5;  // 제안서 p.3, p.11: 방장 포함 5명

    private final int id;
    private final String name;
    private final String host;  // 방장 닉네임 (방을 만든 사람, 제안서 p.4)
    private final Map<String, ClientHandler> members = new LinkedHashMap<>();  // 닉네임 → 연결 (들어온 순서 유지)
    private boolean closed;     // 마감 여부 (2번이 마감 처리 때 바꾼다)
    private boolean deleted;    // 방장이 나가 방이 없어졌는지

    Room(int id, String name, String host, ClientHandler hostConnection) {
        this.id = id;
        this.name = name;
        this.host = host;
        members.put(host, hostConnection);
    }

    int id() {
        return id;
    }

    String name() {
        return name;
    }

    String host() {
        return host;
    }

    /** 방장인지 확인한다. 2번의 CLEAR·CLOSE 권한 검사(NOT_HOST)에 쓴다. */
    boolean isHost(String nickname) {
        return host.equals(nickname);
    }

    synchronized boolean isClosed() {
        return closed;
    }

    /**
     * 마감 상태를 바꾼다. 2번이 CLOSE 처리 때 부른다 (2번 확인 대기).
     * 바꾼 뒤 로비에 바뀐 방 목록을 보내려면 방 락을 놓은 다음 RoomManager.pushRoomList()를 부른다.
     */
    synchronized void setClosed(boolean closed) {
        this.closed = closed;
    }

    synchronized boolean isDeleted() {
        return deleted;
    }

    synchronized void markDeleted() {
        deleted = true;
    }

    synchronized int size() {
        return members.size();
    }

    synchronized boolean isFull() {
        return members.size() >= MAX_MEMBERS;
    }

    synchronized boolean hasMember(String nickname) {
        return members.containsKey(nickname);
    }

    /** 참가자 닉네임 목록 (들어온 순서, 복사본). 2번이 STATUS를 만들 때 쓴다. */
    synchronized List<String> memberNames() {
        return new ArrayList<>(members.keySet());
    }

    /** 참가자 연결 목록 (복사본) */
    synchronized List<ClientHandler> connections() {
        return new ArrayList<>(members.values());
    }

    synchronized void add(String nickname, ClientHandler connection) {
        members.put(nickname, connection);
    }

    synchronized ClientHandler remove(String nickname) {
        return members.remove(nickname);
    }

    synchronized void clearMembers() {
        members.clear();
    }

    /** 이 방 참가자 전원에게 한 줄을 보낸다. 2번의 STATUS·FINAL 전송에도 쓴다. */
    synchronized void broadcast(String line) {
        for (ClientHandler connection : members.values()) {
            connection.send(line);
        }
    }

    /** ROOM_LIST에 들어갈 이 방의 정보 (5번 common 형식: 번호:이름:인원/정원:OPEN|CLOSED) */
    synchronized RoomInfo toInfo() {
        return new RoomInfo(id, name, members.size(), MAX_MEMBERS, closed);
    }
}
