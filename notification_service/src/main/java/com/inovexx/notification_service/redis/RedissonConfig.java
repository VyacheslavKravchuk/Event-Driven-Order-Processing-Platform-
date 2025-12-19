package com.inovexx.notification_service.redis;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // Настройка режима Single Server (самый частый)
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        // Или другие режимы: useClusterServers(), useSentinelServers()...

        return Redisson.create(config);
    }
}
