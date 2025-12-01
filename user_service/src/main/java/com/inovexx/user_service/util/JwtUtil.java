package com.inovexx.user_service.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Key;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class JwtUtil {

    @Value("${spring.security.jwt.secret}")
    private String secret;

    // Ленивая инициализация ключа. Вычисляется только один раз.
    private Key getSigningKey() {
        byte[] keyBytes = secret.getBytes();
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String getUsernameFromToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Boolean isTokenExpired(String token) {
        final Date expiration = getExpirationDateFromToken(token);
        return expiration.before(new Date());
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();

        String roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority) // Получаем строку роли (например, "ROLE_USER")
                .collect(Collectors.joining(","));

        claims.put("roles", roles);

        return doGenerateToken(claims, userDetails.getUsername());
    }

    private String doGenerateToken(Map<String, Object> claims, String subject) {
        long JWT_TOKEN_VALIDITY = 5 * 60 * 60;
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + JWT_TOKEN_VALIDITY * 1000))
                .signWith(getSigningKey(), SignatureAlgorithm.HS512)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = getUsernameFromToken(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    public Collection<? extends GrantedAuthority> extractAuthorities(String token) {
        Claims claims = getAllClaimsFromToken(token);
        if (claims.containsKey("roles")) {
            String rolesString = claims.get("roles", String.class);
            return Arrays.stream(rolesString.split(","))
                    .map(String::trim)
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}