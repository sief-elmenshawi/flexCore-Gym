package com.flexcore.core.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public GenericJackson2JsonRedisSerializer jsonRedisSerializer(ObjectMapper redisObjectMapper) {
        ObjectMapper typed = redisObjectMapper.copy();
        typed.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfSubType("com.flexcore.")
                        .allowIfSubType("java.lang.")
                        .allowIfSubType("java.util.")
                        .allowIfSubType("java.time.")
                        .allowIfSubType("java.math.")
                        .allowIfSubType("java.io.")
                        .build(),
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.WRAPPER_ARRAY
        );
        return new GenericJackson2JsonRedisSerializer(typed);
    }

    @Bean
    public LettucePoolProperties lettucePoolProperties() {
        return new LettucePoolProperties();
    }

    @Bean
    public GenericObjectPoolConfig<StatefulConnection<?, ?>> lettucePoolConfig(LettucePoolProperties lettucePoolProperties) {
        GenericObjectPoolConfig<StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(lettucePoolProperties.getMaxActive());
        poolConfig.setMaxIdle(lettucePoolProperties.getMaxIdle());
        poolConfig.setMinIdle(lettucePoolProperties.getMinIdle());
        return poolConfig;
    }

    @Bean
    public RedisStandaloneConfiguration redisStandaloneConfiguration(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port,
            @Value("${spring.data.redis.password:}") String password) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        if (!password.isBlank()) {
            config.setPassword(RedisPassword.of(password));
        }
        return config;
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory(RedisStandaloneConfiguration redisStandaloneConfiguration,
                                                           GenericObjectPoolConfig<StatefulConnection<?, ?>> lettucePoolConfig) {
        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(lettucePoolConfig)
                .build();
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisStandaloneConfiguration, clientConfig);
        factory.setShareNativeConnection(false);
        return factory;
    }

    @Bean
    @Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                          GenericJackson2JsonRedisSerializer jsonRedisSerializer) {
        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(2))
                .prefixCacheNameWith("flexcore:")
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonRedisSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }

    /**
     * Pool sizing for the Lettuce connection pool. Bound from
     * {@code spring.data.redis.lettuce.pool.*}; names intentionally mirror Spring Boot's
     * {@code max-active / max-idle / min-idle} contract instead of commons-pool2's
     * {@code max-total} so the yml keys stay the ones ops already know.
     */
    @ConfigurationProperties(prefix = "spring.data.redis.lettuce.pool")
    public static class LettucePoolProperties {
        private int maxActive = 8;
        private int maxIdle = 8;
        private int minIdle = 0;

        public int getMaxActive() {
            return maxActive;
        }

        public void setMaxActive(int maxActive) {
            this.maxActive = maxActive;
        }

        public int getMaxIdle() {
            return maxIdle;
        }

        public void setMaxIdle(int maxIdle) {
            this.maxIdle = maxIdle;
        }

        public int getMinIdle() {
            return minIdle;
        }

        public void setMinIdle(int minIdle) {
            this.minIdle = minIdle;
        }
    }
}