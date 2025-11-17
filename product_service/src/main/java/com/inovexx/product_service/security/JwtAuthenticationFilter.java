package com.inovexx.product_service.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    private final WebAuthenticationDetailsSource authenticationDetailsSource = new WebAuthenticationDetailsSource();

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);

        if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
            try {
                // 1. Извлекаем данные (Username, Роли) из токена
                String username = tokenProvider.extractUsername(jwt);
                Claims claims = tokenProvider.extractAllClaims(jwt);

                // 2. Извлекаем роли (предполагая, что они в клейме "roles" через запятую)
                Collection<? extends GrantedAuthority> authorities = Collections.emptyList();
                if (claims.containsKey("roles")) {
                    String rolesString = claims.get("roles", String.class);
                    authorities = Arrays.stream(rolesString.split(","))
                            .map(String::trim) // Удаляем пробелы
                            .map(SimpleGrantedAuthority::new)
                            .collect(Collectors.toList());
                } else {
                    logger.warn("В JWT отсутствует клейм 'roles' для пользователя: {}", username);
                }

                // 3. Создаем объект UserDetails
                UserDetails userDetails = new User(username, "", authorities);

                // 4. Создаем объект аутентификации и помещаем его в контекст Spring Security
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(authenticationDetailsSource.buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                logger.debug("Аутентификация установлена для пользователя: {}", username);

            } catch (Exception e) {
                // Если при парсинге произошла ошибка (например, нет клейма "roles")
                logger.error("Ошибка при установке аутентификации: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        } else if (StringUtils.hasText(jwt)) {
            logger.debug("JWT невалиден: {}", jwt);
        } else {
            logger.debug("JWT отсутствует в запросе.");
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}