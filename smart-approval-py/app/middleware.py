"""限流中间件 —— 令牌桶算法，对应 RateLimitInterceptor.java"""
import time
from collections import defaultdict
from typing import Dict, Tuple

from fastapi import Request, HTTPException, status

from app.config import RATE_LIMIT_PER_MINUTE


class TokenBucket:
    """令牌桶实现"""
    def __init__(self, rate: int, capacity: int):
        self.rate = rate
        self.capacity = capacity
        self.tokens = capacity
        self.last_refill = time.time()

    def refill(self):
        now = time.time()
        elapsed = now - self.last_refill
        self.tokens = min(self.capacity, self.tokens + elapsed * self.rate / 60.0)
        self.last_refill = now

    def consume(self, count: int = 1) -> bool:
        self.refill()
        if self.tokens >= count:
            self.tokens -= count
            return True
        return False


class RateLimiter:
    """每 IP 每分钟最多指定次数的限流器"""
    def __init__(self, per_minute: int = RATE_LIMIT_PER_MINUTE):
        self.buckets: Dict[str, TokenBucket] = {}
        self.per_minute = per_minute

    def _get_bucket(self, key: str) -> TokenBucket:
        if key not in self.buckets:
            self.buckets[key] = TokenBucket(rate=self.per_minute, capacity=self.per_minute)
        return self.buckets[key]

    async def check(self, request: Request):
        """检查是否限流，超限抛出 429"""
        client_ip = request.client.host if request.client else "unknown"
        bucket = self._get_bucket(client_ip)
        if not bucket.consume():
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"请求过于频繁，每IP每分钟限{self.per_minute}次"
            )


rate_limiter = RateLimiter()
