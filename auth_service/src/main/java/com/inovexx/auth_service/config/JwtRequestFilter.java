package com.inovexx.auth_service.config;

import java.io.IOException;

import com.inovexx.auth_service.util.JwtUtil;
import io.jsonwebtoken.io.Decoders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.Collections;
import java.util.List;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.Claims;

import javax.crypto.SecretKey;
import java.security.Key;


@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtRequestFilter.class);

    private final String secret;
    private final JwtUtil jwtUtil;

    public JwtRequestFilter(@Value("${spring.security.jwt.secret}") String secret, JwtUtil jwtUtil) {
        this.secret = secret;
        this.jwtUtil = jwtUtil;
    }

    private SecretKey getSigningKey() {
        logger.info("Используемый секретный ключ (длина): {}", secret.length());
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        final String authorizationHeader = request.getHeader("Authorization");
        String username = null;
        String jwt = null;

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            jwt = authorizationHeader.substring(7);
            try {
                // Пытаемся получить username
                username = jwtUtil.getUsernameFromToken(jwt);
            } catch (ExpiredJwtException e) {
                // Просто логируем, не прерываем цепочку здесь
                logger.error("JWT Token has expired", e);
            } catch (Exception e) {
                logger.warn("Невалидный JWT токен: {}", e.getMessage());
            }
        }

        // Если username получен И пользователь еще не аутентифицирован в контексте
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // Ваш код установки аутентификации в SecurityContextHolder
                Claims claims = Jwts.parserBuilder()
                        .setSigningKey(getSigningKey())
                        .build()
                        .parseClaimsJws(jwt)
                        .getBody();
                String role = (String) claims.get("role");

                if (role != null) {
                    List<SimpleGrantedAuthority> authorities =
                            Collections.singletonList(new SimpleGrantedAuthority(role));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                logger.error("Ошибка при обработке JWT: {}", e.getMessage());
            }
        }

        // В конце всегда пропускаем запрос дальше,
        // а SecurityFilterChain решит, требуется ли аутентификация для этого URL.
        chain.doFilter(request, response);
    }
}