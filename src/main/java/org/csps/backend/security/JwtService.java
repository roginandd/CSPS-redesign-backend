package org.csps.backend.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.csps.backend.domain.dtos.request.SignInCredentialRequestDTO;
import org.csps.backend.domain.entities.Admin;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.enums.UserRole;
import org.csps.backend.service.AdminService;
import org.csps.backend.service.StudentService;
import org.csps.backend.service.UserAccountService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtService {

    @Value("${csps.jwtToken.secretKey}")
    private String secretKey; // Base64-encoded secret key

    @Value("${csps.jwtAccessToken.expireMs}")
    private long jwtAccessTokenExpirationMs; // Expiration time in ms

    private final UserAccountService userAccountService;
    private final StudentService studentService;
    private final AdminService adminService;

    // Decode secret key into HMAC-SHA key
    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    // Extract all claims (payload) from JWT
    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Extract userAccountId (subject) from token
    public Long extractUsernameId(String token) {
        return Long.valueOf(extractAllClaims(token).getSubject());
    }

    // Get expiration date from token
    public Date getExpiration(String token) {
        return extractAllClaims(token).getExpiration();
    }

    // Check if token is expired
    private Boolean isTokenExpired(String token) {
        return getExpiration(token).before(new Date());
    }

    // Core method: build JWT with claims depending on role (Student/Admin)
    private String generateAccessToken(Map<String, Object> customClaim, SignInCredentialRequestDTO studentRequest) {
        // Load account
        UserAccount account = userAccountService.findByUsername(studentRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("UserAccount not found"));

        // Add base claims
        customClaim.put("username", account.getUsername());
        customClaim.put("role", account.getRole().toString());
        customClaim.put("profileId", account.getUserProfile().getUserId());

        // Add role-specific claims
        if (account.getRole() == UserRole.STUDENT) {
            Student student = studentService.findByAccountId(account.getUserAccountId())
                    .orElseThrow(() -> new RuntimeException("Student record not found"));
            customClaim.put("studentId", student.getStudentId());
            customClaim.put("yearLevel", student.getYearLevel());
        } else if (account.getRole() == UserRole.ADMIN) {
            Admin admin = adminService.findByAccountId(account.getUserAccountId())
                    .orElseThrow(() -> new RuntimeException("Admin record not found"));
            customClaim.put("adminId", admin.getAdminId());
            customClaim.put("position", admin.getPosition().name());
        }

    
        // Generate and sign token
        return Jwts.builder()
                .claims(customClaim)
                .subject(String.valueOf(account.getUserAccountId())) // subject = accountId
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtAccessTokenExpirationMs))
                .signWith(getSignInKey())
                .compact();
    }

    // Generate token with empty claims
    public String generateAccessToken(SignInCredentialRequestDTO studentRequest) {
        return generateAccessToken(new HashMap<>(), studentRequest);
    }

    // Validate token: check subject matches and not expired
    public Boolean isTokenValid(String token, SignInCredentialRequestDTO studentRequest) {
        final Long usernameId = extractUsernameId(token);

        UserAccount userEntity = userAccountService.findByUsername(studentRequest.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return (usernameId.equals(userEntity.getUserAccountId()) && !isTokenExpired(token));
    }
}
