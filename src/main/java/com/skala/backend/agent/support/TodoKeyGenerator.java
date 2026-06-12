package com.skala.backend.agent.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * TODO 단건의 식별 키를 생성한다.
 *
 * <p>TODO 내용은 {@code agent_logs.details} JSONB가 단일 원천이며 별도 복사하지 않는다.
 * 확인(체크) 상태만 {@code todo_confirmations} 테이블에 보관하는데, 그 매칭 키가 여기서 만드는 해시다.
 *
 * <p>키 = {@code sha256(usage_statement_id | agent_type_code | usage_statement_item_id | category_code | reason)}.
 * reason이 바뀌면 키가 바뀌어 "새 TODO"로 취급되므로 확인 상태가 자동 해제되고,
 * 내용이 완전히 동일하게 재생성되면 같은 키가 되어 확인 상태가 유지된다.
 *
 * <p>조회(확인 여부 판정)와 토글(저장/삭제) 양쪽이 반드시 동일한 방식으로 키를 계산해야 하므로
 * 단일 진입점으로 둔다.
 */
public final class TodoKeyGenerator {

    private static final String DELIMITER = "|";

    private TodoKeyGenerator() {}

    public static String generate(Long usageStatementId, String agentTypeCode,
            Long usageStatementItemId, String categoryCode, String reason) {
        String canonical = String.join(DELIMITER,
                nullToEmpty(usageStatementId),
                nullToEmpty(agentTypeCode),
                nullToEmpty(usageStatementItemId),
                nullToEmpty(categoryCode),
                nullToEmpty(reason));
        return sha256Hex(canonical);
    }

    private static String nullToEmpty(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
