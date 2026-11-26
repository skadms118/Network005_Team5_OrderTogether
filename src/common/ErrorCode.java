package common;

public enum ErrorCode {
    ALREADY_IN_ROOM,  // 이미 방에 있는데 CREATE/JOIN
    NOT_IN_ROOM,      // 방에 없는데 방 안 요청
    NO_ROOM,          // 없는 방 / 재접속 시 방 사라짐
    ROOM_FULL,        // 5명 초과
    DUP_NICK,         // 같은 방 닉네임 중복
    INVALID_MENU,     // 없는 메뉴 or 수량 오류
    NOT_HOST,         // 방장 아님 (CLOSE, CLEAR)
    CLOSED,           // 마감 후 요청 (LEAVE는 허용)
    INVALID_NAME,     // 빈 이름 or | , : 포함
    INVALID_TOKEN,    // 토큰 오류/만료
    BAD_REQUEST       // 모르는 명령, 필드 부족, 숫자 아님 등 형식 오류
}
