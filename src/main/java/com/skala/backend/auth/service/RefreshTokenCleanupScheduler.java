package com.skala.backend.auth.service;

import com.skala.backend.auth.repository.RefreshTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 만료된 refresh token 행을 주기적으로 정리한다.
 * <p>
 * 로그인/재발급마다 행이 쌓이고 만료된 행도 자동 삭제되지 않아 {@code refresh_tokens} 테이블이 무한 증가하는 것을 방지한다.
 * {@code expires_at < now} 인 행만 삭제하므로, 아직 유효 기간이 남은 revoke 토큰은 보존되어
 * {@link RefreshTokenService#rotate(String)} 의 재사용 탐지(reuse detection) 윈도우를 침범하지 않는다.
 */
@Component
public class RefreshTokenCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanupScheduler.class);

	private final RefreshTokenRepository refreshTokenRepository;

	public RefreshTokenCleanupScheduler(RefreshTokenRepository refreshTokenRepository) {
		this.refreshTokenRepository = refreshTokenRepository;
	}

	@Scheduled(cron = "${app.auth.refresh-token-cleanup.cron:0 0 3 * * *}", zone = "Asia/Seoul")
	@Transactional
	public void purgeExpiredTokens() {
		int deleted = refreshTokenRepository.deleteAllExpiredBefore(Instant.now());
		if (deleted > 0) {
			log.info("Purged {} expired refresh token(s).", deleted);
		}
	}
}
