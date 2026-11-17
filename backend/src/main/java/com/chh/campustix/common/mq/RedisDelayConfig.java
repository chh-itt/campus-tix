package com.chh.campustix.common.mq;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** delay-impl=redis 时才装配 Redisson 客户端（Redisson 延迟队列实现用） */
@Configuration
@ConditionalOnProperty(name = "campustix.order.delay-impl", havingValue = "redis")
public class RedisDelayConfig {

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties props) {
        Config config = new Config();
        String addr = "redis://" + props.getHost() + ":" + props.getPort();
        var server = config.useSingleServer().setAddress(addr);
        if (props.getPassword() != null && !props.getPassword().isBlank()) {
            server.setPassword(props.getPassword());
        }
        return Redisson.create(config);
    }
}
