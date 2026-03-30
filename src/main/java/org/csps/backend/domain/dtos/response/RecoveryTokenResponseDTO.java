package org.csps.backend.domain.dtos.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecoveryTokenResponseDTO {
    
    private Long recoveryTokenId;
    
    private Long userAccountId;

    private LocalDateTime createdAt;
    
    @JsonAlias("expiresAt")
    private LocalDateTime expiresAt;
    
    @JsonAlias("isUsed")
    private Boolean isUsed;
    
    private LocalDateTime usedAt;
    
    /**
     * check if token is still valid
     */
    public boolean isValid() {
        return !isUsed && LocalDateTime.now().isBefore(expiresAt);
    }
}
