package org.example.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redisClient() {
        //创建一个redis的配置类
        Config config = new Config();
        //使用单点连接而非集群连接，设置连接ip端口，连接密码
        config.useSingleServer()
                .setAddress("redis://116.198.196.226:6379")
                .setPassword("95Aip2cYeXsmqgm7ZTQq");

        return Redisson.create(config);
    }
}
