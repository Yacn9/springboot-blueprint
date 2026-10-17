package com.acme.blueprint.shared.cache;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import net.rubyeye.xmemcached.MemcachedClient;
import net.rubyeye.xmemcached.XMemcachedClientBuilder;
import net.rubyeye.xmemcached.exception.MemcachedException;
import net.rubyeye.xmemcached.utils.AddrUtil;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.AbstractCacheManager;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Memcached behind Spring's cache abstraction. Spring Boot supports Redis and
 * Caffeine but not Memcached, so this is the adapter: one {@link Cache} per
 * cache name, each entry stored under {@code <name>:<key>} for
 * {@code blueprint.cache.memcached.expiry}. xmemcached serialises values with
 * Java serialization, so whatever is cached must be {@code Serializable}.
 * Spring Boot's own cache configuration steps aside for this CacheManager.
 */
@Configuration(proxyBeanMethods = false)
class MemcachedCacheConfiguration {

    @Bean(destroyMethod = "shutdown")
    MemcachedClient memcachedClient(@Value("${blueprint.cache.memcached.servers}") String servers) throws IOException {
        return new XMemcachedClientBuilder(AddrUtil.getAddresses(servers)).build();
    }

    @Bean
    CacheManager cacheManager(MemcachedClient client, @Value("${blueprint.cache.memcached.expiry}") Duration expiry) {
        return new AbstractCacheManager() {
            @Override
            protected Collection<? extends Cache> loadCaches() {
                return List.of();
            }

            @Override
            protected Cache getMissingCache(String name) {
                return new MemcachedCache(name, client, Math.toIntExact(expiry.toSeconds()));
            }
        };
    }

    static final class MemcachedCache extends AbstractValueAdaptingCache {

        private final String name;
        private final MemcachedClient client;
        private final int expirySeconds;

        MemcachedCache(String name, MemcachedClient client, int expirySeconds) {
            super(false);
            this.name = name;
            this.client = client;
            this.expirySeconds = expirySeconds;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public Object getNativeCache() {
            return client;
        }

        @Override
        protected Object lookup(Object key) {
            return call(() -> client.get(keyOf(key)));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T get(Object key, Callable<T> valueLoader) {
            Object cached = lookup(key);
            if (cached != null) {
                return (T) fromStoreValue(cached);
            }
            T value;
            try {
                value = valueLoader.call();
            } catch (Exception failure) {
                throw new ValueRetrievalException(key, valueLoader, failure);
            }
            put(key, value);
            return value;
        }

        @Override
        public void put(Object key, Object value) {
            if (value == null) {
                evict(key);
                return;
            }
            call(() -> client.set(keyOf(key), expirySeconds, toStoreValue(value)));
        }

        @Override
        public void evict(Object key) {
            call(() -> client.delete(keyOf(key)));
        }

        /** ponytail: flushes the whole server, every cache's entries; give caches their own servers if that matters. */
        @Override
        public void clear() {
            call(() -> {
                client.flushAll();
                return null;
            });
        }

        private String keyOf(Object key) {
            return name + ":" + key;
        }

        private static <T> T call(MemcachedCall<T> call) {
            try {
                return call.run();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while talking to Memcached", interrupted);
            } catch (TimeoutException | MemcachedException failure) {
                throw new IllegalStateException("Memcached call failed: " + failure.getMessage(), failure);
            }
        }

        @FunctionalInterface
        private interface MemcachedCall<T> {
            T run() throws TimeoutException, InterruptedException, MemcachedException;
        }
    }
}
