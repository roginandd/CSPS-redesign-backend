package org.csps.backend.security;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.csps.backend.domain.entities.Admin;
import org.csps.backend.domain.entities.Student;
import org.csps.backend.domain.entities.UserAccount;
import org.csps.backend.domain.enums.UserRole;
import org.csps.backend.service.AdminService;
import org.csps.backend.service.StudentService;
import org.springframework.beans.factory.annotation.Value;
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

    private long jwtAccessTokenExpirationMs = 120000; // Expiration time in ms

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
    private String generateAccessToken(Map<String, Object> customClaim, UserAccount user) {
        

        // Add base claims
        customClaim.put("role", user.getRole().toString());

        // Add role-specific claims
        if (user.getRole() == UserRole.STUDENT) {
            Student student = studentService.findByAccountId(user.getUserAccountId())
                    .orElseThrow(() -> new RuntimeException("Student record not found"));
            customClaim.put("studentId", student.getStudentId());
        } else if (user.getRole() == UserRole.ADMIN) {
            Admin admin = adminService.findByAccountId(user.getUserAccountId())
                    .orElseThrow(() -> new RuntimeException("Admin record not found"));
            customClaim.put("position", admin.getPosition().name());
        }

    
        // Generate and sign token
        return Jwts.builder()
                .claims(customClaim)
                .subject(String.valueOf(user.getUserAccountId())) // subject = accountId
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtAccessTokenExpirationMs))
                .signWith(getSignInKey())
                .compact();
    }

    // Generate token with empty claims
    public String generateAccessToken(UserAccount user) {
        return generateAccessToken(new HashMap<>(), user);
    }

    // Validate token: check subject matches and not expired
    public Boolean isTokenValid(String token, UserAccount user) {
        final Long usernameId = extractUsernameId(token);

        return (usernameId.equals(user.getUserAccountId()) && !isTokenExpired(token));
    }
}
