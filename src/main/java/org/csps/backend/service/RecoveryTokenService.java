package org.csps.backend.service;

import org.csps.backend.domain.dtos.request.PasswordRecoveryRequestDTO;
import org.csps.backend.domain.entities.RecoveryToken;
import org.csps.backend.domain.entities.UserAccount;

public interface RecoveryTokenService {
    
    /**
     * generate a new recovery token for user
     */
    RecoveryToken generateRecoveryToken(PasswordRecoveryRequestDTO requestDTO);
    
    /**
     * validate recovery token by token string
     */
    RecoveryToken validateRecoveryToken(String token);
    
    /**
     * mark recovery token as used
     */
    void markTokenAsUsed(String token);

    /**
     * reset a user's password and consume the recovery token atomically
     */
    void resetPassword(String token, String newPassword);
    
    /**
     * get valid recovery token for user
     */
    RecoveryToken getValidTokenForUser(UserAccount userAccount);
    
    /**
     * revoke all valid tokens for user
     */
    void revokeAllTokensForUser(UserAccount userAccount);
    
    /**
     * clean up expired tokens
     */
    void cleanupExpiredTokens();
}
