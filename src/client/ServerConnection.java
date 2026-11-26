package client;

import java.io.*;
import java.net.*;

/*
 * 서버와의 TCP 연결 하나.
 *
 *   - 한글(닉네임, 메뉴 이름) 때문에 보낼 때와 받을 때 모두 UTF-8 을 쓴다.
 *     (DataOutputStream.writeBytes 는 글자 하나를 1바이트로만 보내서 한글이 깨진다)
 *   - send() 에 synchronized 를 붙였다.
 *     입력 스레드(주문 요청)와 수신 스레드(PONG 응답)가
 *     동시에 보내도 두 메시지가 섞이지 않는다.
 */
class ServerConnection {

    private Socket socket;
    private BufferedReader inFromServer;
    private PrintWriter outToServer;

    ServerConnection(String host, int port) throws IOException {
        // create client socket, connect to server
        socket = new Socket(host, port);
        // 소켓의 입력/출력 스트림을 UTF-8 로 연다
        inFromServer = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        outToServer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
    }

    // 메시지 한 줄 보내기.
    // 프로토콜 규칙: 메시지 끝은 "\n" 하나.
    // (println 은 Windows 에서 "\r\n" 을 붙이므로 쓰지 않고 "\n" 을 직접 붙인다)
    synchronized void send(String msg) {
        outToServer.print(msg + "\n");
        outToServer.flush();
    }

    // 서버 메시지 한 줄 읽기 (수신 스레드만 부른다). 연결이 끊기면 null
    String readLine() throws IOException {
        return inFromServer.readLine();
    }

    void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // 이미 닫혀 있으면 무시
        }
    }
}
