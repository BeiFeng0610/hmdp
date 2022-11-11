package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 16116
 */
@Configuration
public class RedissonConfig {

    @Value("${redis.host}")
    private String redisHost;
    @Value("${redis.password}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient(){
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":6379").setPassword(redisPassword);
        return Redisson.create(config);
    }
}
