package com.example.seat_booking_system.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// Step 2: JWT utility — generates and validates JSON Web Tokens (HS256 signed).
// JWT structure: HEADER.PAYLOAD.SIGNATURE — payload contains username, role, iat, exp.
// Stateless auth: no session storage; any instance can validate a token using the shared secret.
@Component
@Slf4j
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    // Step 2.1: Build the HS256 signing key from the configured secret (must be ≥256 bits).
    public JwtUtil(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    // Step 2.2: Generates a signed JWT with sub (username), role, iat, and exp claims.
    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        // Embed role as a custom claim — read by the auth filter to set authorities.
        claims.put("role", userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("ROLE_USER"));

        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername())   // "sub" claim — identifies the user
                .issuedAt(new Date())                  // "iat" claim
                .expiration(new Date(System.currentTimeMillis() + expirationMs)) // "exp" claim
                .signWith(signingKey)                  // signs with HS256
                .compact();                            // produces the Base64URL-encoded string
    }

    // Step 2.3: Extracts the username (sub claim) from the token.
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Step 2.4: Returns true if signature is valid AND token has not expired.
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Step 2.5: Parses and verifies signature — throws JwtException subtypes on failure
    //           (ExpiredJwtException, MalformedJwtException, SignatureException).
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Expiration in ms — exposed for building the AuthResponse.
    public long getExpirationMs() {
        return expirationMs;
    }
}
