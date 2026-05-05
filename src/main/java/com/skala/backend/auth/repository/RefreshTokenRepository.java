package com.skala.backend.auth.repository;

import com.skala.backend.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	Optional<RefreshToken> findByTokenHash(String tokenHash);

	void deleteByUserId(Long userId);

	@Modifying
	@Query("""
			UPDATE RefreshToken token
			SET token.revokedAt = :revokedAt
			WHERE token.user.id = :userId
				AND token.revokedAt IS NULL
			""")
	void revokeActiveTokensByUserId(@Param("userId") Long userId, @Param("revokedAt") Instant revokedAt);
}
