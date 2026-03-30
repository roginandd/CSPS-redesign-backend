package org.csps.backend.service;

import java.util.Optional;

import org.csps.backend.domain.entities.RefreshToken;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(Long userId);
    Optional<RefreshToken> findByRefreshToken(String token);
    boolean isValidRefreshToken(RefreshToken token);
    void deleteRefreshToken(RefreshToken token);
    void deleteByUserId(Long userId);
    Optional<String> refreshAccessToken(String refreshToken);

}
