package org.csps.backend.controller;

import java.util.Map;

import org.csps.backend.domain.dtos.request.ChangePasswordRequestDTO;
import org.csps.backend.domain.dtos.request.SignInCredentialRequestDTO;
import org.csps.backend.domain.dtos.request.UpdateEmailRequestDTO;
import org.csps.backend.domain.dtos.response.AdminResponseDTO;
import org.csps.backend.domain.dtos.response.AuthResponseDTO;
import org.csps.backend.domain.dtos.response.GlobalResponseBuilder;
import org.csps.backend.domain.dtos.response.StudentResponseDTO;
import org.csps.backend.domain.entities.EmailVerification;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.security.JwtService;
import org.csps.backend.service.AdminService;
import org.csps.backend.service.EmailVerificationService;
import org.csps.backend.service.StudentService;
import org.csps.backend.service.UserAccountService;
import org.csps.backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAccountService userAccountService;
    private final UserService userService;
    private final EmailVerificationService emailVerificationService; // Inject EmailVerificationService
    private final JwtService jwtService;
    private final StudentService studentService;
    private final AdminService adminService;
    private final PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<GlobalResponseBuilder<AuthResponseDTO>> login(@Valid @RequestBody SignInCredentialRequestDTO signInRequest) {
        String studentId = signInRequest.getStudentId();
        
        UserAccount user = userAccountService.findByUsername(studentId)
                .orElse(null);

        boolean isCorrectPassword = user != null && passwordEncoder.matches(signInRequest.getPassword(), user.getPassword());

        if (!isCorrectPassword) {
            return GlobalResponseBuilder.buildResponse(
                "Invalid credentials",
                null,
                HttpStatus.UNAUTHORIZED
            );
        }
        

        String accessToken = jwtService.generateAccessToken(user);

        // Return both tokens in response body - client should store in memory or secure storage
        AuthResponseDTO authResponse = AuthResponseDTO.builder()
                .accessToken(accessToken)
                .build();

        return GlobalResponseBuilder.buildResponse(
            "Login successful",
            authResponse,
            HttpStatus.OK
        );
    }

    // logout
    @PostMapping("/logout")
    public ResponseEntity<GlobalResponseBuilder<String>> logout() {
        return GlobalResponseBuilder.buildResponse(
            "Logout successful",
            null,
            HttpStatus.OK
        );
    }

    // change password
    @PostMapping("/change-password")
    public ResponseEntity<GlobalResponseBuilder<String>> changePassword(
        Authentication authentication,
        @Valid @RequestBody ChangePasswordRequestDTO requestDTO
    ) {

        Long userId = (Long) authentication.getCredentials();  // Cast to Long

        userService.changePassword(userId, requestDTO);
        
        return GlobalResponseBuilder.buildResponse(
            "Password changed successfully",
            null,
            HttpStatus.OK
        );
    }

    // get student profile
    @GetMapping("/profile")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> studentProfile(@AuthenticationPrincipal String studentId) {
        // get student by id
        StudentResponseDTO student = studentService.findById(studentId)
                .orElseThrow(() -> new UsernameNotFoundException("Student not found"));
        return ResponseEntity.ok(student);
    }

    @GetMapping("/admin/profile")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminProfile(@AuthenticationPrincipal Long adminId) {
        // get admin by id
        AdminResponseDTO admin = adminService.findById(adminId)
                .orElseThrow(() -> new UsernameNotFoundException("Admin not found"));
        // return admin
        return ResponseEntity.ok(admin);
    }

    /**
     * Initiates the email update verification process by sending a code to the user's current email.
     * Requires the user to be authenticated and their account to be verified.
     */
    @PostMapping("/email/update/initiate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GlobalResponseBuilder<EmailVerification>> initiateEmailUpdate(
            Authentication authentication,
            @RequestBody Map<String, String> requestBody) {
        // Get userAccountId from authentication
        Long userAccountId = (Long) authentication.getCredentials();
        String newEmail = requestBody.get("newEmail");

        // Call service
        EmailVerification emailVerification = emailVerificationService.initiateEmailUpdate(userAccountId, newEmail);

        return GlobalResponseBuilder.buildResponse(
                "Verification code sent to your current email for update to: " + newEmail,
                emailVerification,
                HttpStatus.OK);
    }

    /**
     * Confirms the email update using the provided verification code.
     * Requires the user to be authenticated.
     */
    @PostMapping("/email/update/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<GlobalResponseBuilder<EmailVerification>> confirmEmailUpdate(
            Authentication authentication,
            @Valid @RequestBody UpdateEmailRequestDTO requestDTO) {
        // Get userAccountId from authentication
        Long userAccountId = (Long) authentication.getCredentials();

        // Call service
        EmailVerification emailVerification = emailVerificationService.confirmEmailUpdate(
                userAccountId, requestDTO.getNewEmail(), requestDTO.getVerificationCode());

        return GlobalResponseBuilder.buildResponse(
                "Email updated successfully to: " + requestDTO.getNewEmail(),
                emailVerification,
                HttpStatus.OK);
    }
}
