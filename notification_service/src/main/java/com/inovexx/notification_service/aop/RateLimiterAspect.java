package com.inovexx.notification_service.aop;

import com.inovexx.notification_service.redis.RateLimiterService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

@Aspect
@Component
public class RateLimiterAspect {

    @Autowired
    private RateLimiterService rateLimiterService;

    @Around("@annotation(RateLimited)")  // Применяется к методам, помеченным аннотацией @RateLimited
    public Object rateLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                .getRequest();
        String userId = request.getHeader("X-User-ID");  // Получаем ID пользователя из заголовка

        if (userId == null || userId.isEmpty()) {
            return new ResponseEntity<>("User ID is required in header 'X-User-ID'", HttpStatus.BAD_REQUEST);
        }

        if (rateLimiterService.allowRequest(userId)) {
            return joinPoint.proceed(); // Продолжаем выполнение метода
        } else {
            return new ResponseEntity<>("Too many requests", HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}

