package com.catalog.common.config;

import com.catalog.common.cache.CacheNames;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private final RedisConnectionFactory connectionFactory;
    private final long categoriesTtlHours;
    private final long brandsTtlHours;
    private final long productsTtlMinutes;
    private final long attributeTypesTtlHours;
    private final long productSearchTtlSeconds;

    public RedisConfig(
            RedisConnectionFactory connectionFactory,
            @Value("${catalog.cache.categories-ttl-hours:2}") long categoriesTtlHours,
            @Value("${catalog.cache.brands-ttl-hours:1}") long brandsTtlHours,
            @Value("${catalog.cache.products-ttl-minutes:30}") long productsTtlMinutes,
            @Value("${catalog.cache.attribute-types-ttl-hours:4}") long attributeTypesTtlHours,
            @Value("${catalog.cache.product-search-ttl-seconds:120}") long productSearchTtlSeconds) {
        this.connectionFactory = connectionFactory;
        this.categoriesTtlHours = categoriesTtlHours;
        this.brandsTtlHours = brandsTtlHours;
        this.productsTtlMinutes = productsTtlMinutes;
        this.attributeTypesTtlHours = attributeTypesTtlHours;
        this.productSearchTtlSeconds = productSearchTtlSeconds;
    }

    @Bean("redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var validator = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.catalog")
                .allowIfSubType("java.lang")
                .allowIfSubType("java.time")
                .allowIfSubType("java.util")
                .allowIfSubType("java.math")
                .build();
        mapper.activateDefaultTyping(
                validator,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    private RedisCacheConfiguration defaultCacheConfig(ObjectMapper redisObjectMapper) {
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        return RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .disableCachingNullValues();
    }

    @Override
    public CacheManager cacheManager() {
        ObjectMapper redisObjectMapper = redisObjectMapper();
        RedisCacheConfiguration defaults = defaultCacheConfig(redisObjectMapper);

        Map<String, RedisCacheConfiguration> configs = new HashMap<>();
        configs.put(CacheNames.CATEGORIES, defaults.entryTtl(Duration.ofHours(categoriesTtlHours)));
        configs.put(CacheNames.BRANDS, defaults.entryTtl(Duration.ofHours(brandsTtlHours)));
        configs.put(CacheNames.PRODUCTS, defaults.entryTtl(Duration.ofMinutes(productsTtlMinutes)));
        configs.put(CacheNames.ATTRIBUTE_TYPES, defaults.entryTtl(Duration.ofHours(attributeTypesTtlHours)));
        configs.put(CacheNames.PRODUCT_SEARCH, defaults.entryTtl(Duration.ofSeconds(productSearchTtlSeconds)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaults.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(configs)
                .transactionAware()
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException ex, Cache cache, Object key) {
                log.warn("Redis GET failed - cache='{}' key='{}'. Falling through to DB. Error: {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException ex, Cache cache, Object key, Object value) {
                log.warn("Redis PUT failed - cache='{}' key='{}'. DB write succeeded. Error: {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException ex, Cache cache, Object key) {
                log.error("Redis EVICT failed - cache='{}' key='{}'. Cache may be stale until TTL expiry. Error: {}", cache.getName(), key, ex.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException ex, Cache cache) {
                log.error("Redis CLEAR failed - cache='{}'. Error: {}", cache.getName(), ex.getMessage());
            }
        };
    }
}
