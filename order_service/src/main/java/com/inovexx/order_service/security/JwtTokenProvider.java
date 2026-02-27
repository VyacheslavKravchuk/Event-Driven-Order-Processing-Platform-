package com.inovexx.order_service.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtTokenProvider {

    @Value("${spring.security.jwt.secret}")
    private String secretString;

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    // В 0.12.x рекомендуется использовать SecretKey напрямую
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretString);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // --- Методы для парсинга и валидации ---

    public Claims extractAllClaims(String token) {
        return Jwts.parser() // Теперь просто .parser() вместо .parserBuilder()
                .verifyWith(getSigningKey()) // verifyWith() вместо setSigningKey()
                .build()
                .parseSignedClaims(token) // parseSignedClaims() вместо parseClaimsJws()
                .getPayload(); // getPayload() вместо getBody()
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException e) {
            logger.error("Неверная JWT подпись: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            logger.error("Некорректный JWT формат: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            logger.warn("Срок действия JWT истек: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            logger.error("JWT не поддерживается: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error("JWT строка пуста: {}", e.getMessage());
        }
        return false;
    }
}

