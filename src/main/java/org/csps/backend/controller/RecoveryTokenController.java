package org.csps.backend.controller;

import java.util.Map;

import org.csps.backend.domain.dtos.request.PasswordRecoveryRequestDTO;
import org.csps.backend.domain.dtos.request.PasswordResetRequestDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.RecoveryTokenResponseDTO;
import org.csps.backend.mapper.RecoveryTokenMapper;
import org.csps.backend.service.RecoveryTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/recovery-token")
@RequiredArgsConstructor
@Slf4j
public class RecoveryTokenController {

    private final RecoveryTokenService recoveryTokenService;
    private final RecoveryTokenMapper recoveryTokenMapper;

    @PostMapping("/request")
    public ResponseEntity<GlobalResponseBuilder<RecoveryTokenResponseDTO>> requestRecovery(
            @Valid @RequestBody PasswordRecoveryRequestDTO requestDTO) {

        try {
            recoveryTokenService.generateRecoveryToken(requestDTO);
            return GlobalResponseBuilder.buildResponse(
                "Recovery token sent to email",
                null,
                HttpStatus.OK
            );
        } catch (org.csps.backend.exception.ResourceNotFoundException e) {
            return GlobalResponseBuilder.buildResponse(
                e.getMessage(),
                null,
                HttpStatus.NOT_FOUND
            );
        } catch (Exception e) {
            log.error("failed to request recovery: {}", e.getMessage(), e);
            return GlobalResponseBuilder.buildResponse(
                "Failed to process recovery request",
                null,
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<GlobalResponseBuilder<RecoveryTokenResponseDTO>> validateToken(
            @RequestBody Map<String, String> request) {

        try {
            String token = request.get("token");
            if (token == null || token.isBlank()) {
                return GlobalResponseBuilder.buildResponse(
                    "Recovery token is required",
                    null,
                    HttpStatus.BAD_REQUEST
                );
            }

            RecoveryTokenResponseDTO responseDTO = recoveryTokenMapper.toResponseDTO(
                    recoveryTokenService.validateRecoveryToken(token));
            return GlobalResponseBuilder.buildResponse(
                "Recovery token is valid",
                responseDTO,
                HttpStatus.OK
            );
        } catch (org.csps.backend.exception.ResourceNotFoundException e) {
            return GlobalResponseBuilder.buildResponse(
                "Recovery token not found",
                null,
                HttpStatus.NOT_FOUND
            );
        } catch (org.csps.backend.exception.InvalidRequestException e) {
            return GlobalResponseBuilder.buildResponse(
                e.getMessage(),
                null,
                HttpStatus.BAD_REQUEST
            );
        }
    }

    @PostMapping("/reset-password")
    public ResponseEntity<GlobalResponseBuilder<String>> resetPassword(
            @Valid @RequestBody PasswordResetRequestDTO requestDTO) {

        try {
            if (!requestDTO.getNewPassword().equals(requestDTO.getConfirmPassword())) {
                return GlobalResponseBuilder.buildResponse(
                    "Passwords do not match",
                    null,
                    HttpStatus.BAD_REQUEST
                );
            }

            recoveryTokenService.resetPassword(requestDTO.getToken(), requestDTO.getNewPassword());

            return GlobalResponseBuilder.buildResponse(
                "Password reset successfully",
                null,
                HttpStatus.OK
            );
        } catch (org.csps.backend.exception.ResourceNotFoundException e) {
            return GlobalResponseBuilder.buildResponse(
                "Recovery token not found or expired",
                null,
                HttpStatus.NOT_FOUND
            );
        } catch (org.csps.backend.exception.InvalidRequestException e) {
            return GlobalResponseBuilder.buildResponse(
                e.getMessage(),
                null,
                HttpStatus.BAD_REQUEST
            );
        } catch (Exception e) {
            log.error("failed to reset password: {}", e.getMessage(), e);
            return GlobalResponseBuilder.buildResponse(
                "Failed to reset password",
                null,
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
