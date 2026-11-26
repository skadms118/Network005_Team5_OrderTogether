package server;

import java.security.SecureRandom;
import java.util.Base64;

public class TokenManager {

    // 보안용 난수 생성기
    private final SecureRandom random = new SecureRandom();

    // 새로운 재접속 토큰 발급
    public String generateToken() {

        byte[] bytes = new byte[32];

        random.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    // 토큰 형식 및 값 확인
    public boolean isValidToken(String token) {

        if (token == null || token.isBlank()) {
            return false;
        }

        // 32바이트 난수를 Base64 URL 형식으로 변환하면
        // 패딩 없이 43글자가 생성됨
        return token.matches("[A-Za-z0-9_-]{43}");
    }

    // 입력한 토큰과 저장된 토큰 비교
    public boolean matches(String inputToken, String savedToken) {

        if (!isValidToken(inputToken) || !isValidToken(savedToken)) {
            return false;
        }

        return java.security.MessageDigest.isEqual(
                inputToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                savedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }
}