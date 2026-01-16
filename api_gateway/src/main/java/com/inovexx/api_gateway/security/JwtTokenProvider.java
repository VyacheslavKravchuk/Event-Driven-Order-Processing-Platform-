package com.inovexx.api_gateway.security;

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

    @Value("${jwt.secret-key}")
    private String secretKey;

    private static final Logger logger =
            LoggerFactory.getLogger(JwtTokenProvider.class);

    // Метод для получения ключа подписи из Base64 строки
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    // --- Методы для парсинга и валидации ---

    // Метод для извлечения всех данных (Claims) из токена
    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder() // Получаем Builder
                .setSigningKey(getSigningKey()) // Устанавливаем ключ подписи
                .build() // Собираем парсер
                .parseClaimsJws(token) // Парсим токен
                .getBody(); // Получаем тело (Claims)
    }

    // Извлечение имени пользователя (Subject)
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // Универсальный метод извлечения конкретного клейма
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // Проверка срока действия
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Метод валидации токена с обработкой исключений
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey())
                    .build().parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            logger.error("Неверная JWT подпись: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            logger.error("Некорректный JWT формат: {}", e.getMessage());
            return false;
        } catch (ExpiredJwtException e) {
            logger.warn("Срок действия JWT истек: {}", e.getMessage()); // Используем warn, так как это ожидаемое поведение
            return false;
        } catch (UnsupportedJwtException e) {
            logger.error("JWT не поддерживается: {}", e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            logger.error("JWT строка пуста: {}", e.getMessage());
            return false;
        }
    }
}
