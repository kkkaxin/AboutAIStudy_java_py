package com.smartapproval.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单令牌桶限流 - 控制AI调用频率
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    @Value("${approval.rate-limit.per-minute:10}")
    private int maxPerMinute;

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        // 只对AI审批接口限流
        if (!request.getRequestURI().contains("/approval/ai")) {
            return true;
        }

        String key = request.getRemoteAddr();
        TokenBucket bucket = buckets.computeIfAbsent(key, 
                k -> new TokenBucket(maxPerMinute));

        if (!bucket.tryAcquire()) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        return true;
    }

    /**
     * 简易令牌桶
     */
    private static class TokenBucket {
        private final int capacity;
        private final AtomicInteger tokens;
        private volatile long lastRefillTime;

        TokenBucket(int capacity) {
            this.capacity = capacity;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }

        synchronized boolean tryAcquire() {
            refill();
            if (tokens.get() > 0) {
                tokens.decrementAndGet();
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTime;
            // 每60秒补充到满
            if (elapsed > 60_000) {
                tokens.set(capacity);
                lastRefillTime = now;
            }
        }
    }
}
