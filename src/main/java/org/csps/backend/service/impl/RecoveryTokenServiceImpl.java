package org.csps.backend.service.impl;

import java.time.LocalDateTime;
import java.util.UUID;

import org.csps.backend.domain.dtos.request.PasswordRecoveryRequestDTO;
import org.csps.backend.domain.entities.RecoveryToken;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.entities.UserProfile;
import org.csps.backend.exception.InvalidRequestException;
import org.csps.backend.exception.ResourceNotFoundException;
import org.csps.backend.repository.RecoveryTokenRepository;
import org.csps.backend.repository.UserAccountRepository;
import org.csps.backend.repository.UserProfileRepository;
import org.csps.backend.service.EmailService;
import org.csps.backend.service.RecoveryTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecoveryTokenServiceImpl implements RecoveryTokenService {

    private final RecoveryTokenRepository recoveryTokenRepository;
    private final UserAccountRepository userAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${FRONTEND_URL}")
    private String frontendUrl;

    @Value("${csps.recovery.token.expiration.minutes:60}")
    private long tokenExpirationMinutes;

    @Override
    @Transactional
    public RecoveryToken generateRecoveryToken(PasswordRecoveryRequestDTO requestDTO) {
        UserProfile userProfile = userProfileRepository.findByEmail(requestDTO.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("user not found with email: " + requestDTO.getEmail()));

        UserAccount userAccount = userProfile.getUserAccounts().stream()
                .filter(UserAccount::getIsVerified)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("no verified user account found for email: " + requestDTO.getEmail()));

        if (userAccount.getUserAccountId() == null) {
            throw new InvalidRequestException("user account is required");
        }

        if (!userAccount.getIsVerified()) {
            throw new InvalidRequestException("user account is not verified");
        }

        revokeAllTokensForUser(userAccount);

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(tokenExpirationMinutes);

        String recoveryLink = buildRecoveryLink(token);
        String userName = userProfile.getFirstName() != null ? userProfile.getFirstName() : userAccount.getUsername();

        emailService.sendPasswordRecoveryEmail(requestDTO.getEmail(), userName, recoveryLink);

        RecoveryToken recoveryToken = RecoveryToken.builder()
                .userAccount(userAccount)
                .token(token)
                .createdAt(now)
                .expiresAt(expiresAt)
                .isUsed(false)
                .build();

        RecoveryToken savedToken = recoveryTokenRepository.save(recoveryToken);
        log.info("recovery token generated for user: {}", userAccount.getUserAccountId());
        return savedToken;
    }

    @Override
    public RecoveryToken validateRecoveryToken(String token) {
        if (token == null || token.isEmpty()) {
            throw new InvalidRequestException("recovery token is required");
        }

        RecoveryToken recoveryToken = recoveryTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("recovery token not found"));

        if (!recoveryToken.isValid()) {
            log.warn("recovery token is invalid or expired: {}", token);
            throw new InvalidRequestException("recovery token is invalid or expired");
        }

        return recoveryToken;
    }

    @Override
    @Transactional
    public void markTokenAsUsed(String token) {
        RecoveryToken recoveryToken = validateRecoveryToken(token);
        recoveryTokenRepository.markAsUsed(recoveryToken.getRecoveryTokenId());
        log.info("recovery token marked as used: {}", token);
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        RecoveryToken recoveryToken = validateRecoveryToken(token);
        UserAccount userAccount = recoveryToken.getUserAccount();

        userAccount.setPassword(passwordEncoder.encode(newPassword));
        userAccountRepository.save(userAccount);
        recoveryTokenRepository.markAsUsed(recoveryToken.getRecoveryTokenId());

        log.info("password reset successfully for user: {}", userAccount.getUserAccountId());
    }

    @Override
    public RecoveryToken getValidTokenForUser(UserAccount userAccount) {
        if (userAccount == null || userAccount.getUserAccountId() == null) {
            throw new InvalidRequestException("user account is required");
        }

        return recoveryTokenRepository.findValidTokenByUserAccountId(userAccount.getUserAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("no valid recovery token found for user"));
    }

    @Override
    @Transactional
    public void revokeAllTokensForUser(UserAccount userAccount) {
        if (userAccount == null || userAccount.getUserAccountId() == null) {
            return;
        }

        recoveryTokenRepository.findValidTokenByUserAccountId(userAccount.getUserAccountId())
                .ifPresentOrElse(
                    token -> {
                        recoveryTokenRepository.markAsUsed(token.getRecoveryTokenId());
                        log.info("revoked recovery token for user: {}", userAccount.getUserAccountId());
                    },
                    () -> log.debug("no valid tokens to revoke for user: {}", userAccount.getUserAccountId())
                );
    }

    @Override
    @Transactional
    public void cleanupExpiredTokens() {
        log.info("cleaning up expired recovery tokens");
        recoveryTokenRepository.deleteExpiredTokens();
    }

    private String buildRecoveryLink(String token) {
        return frontendUrl + "/reset-password?token=" + token;
    }
}
