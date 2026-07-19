package xiaozhi.common.redis;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

/**
 * 本地缓存工具类（基于 Caffeine 实现，替代原 Redis 实现）。
 *
 * <p>该类保留原有方法签名，调用方无需改动。语义对齐原 Redis 版本：
 * <ul>
 *   <li>每个 key 可单独设置过期时间（秒），{@link #NOT_EXPIRE} 表示永不过期；</li>
 *   <li>{@code increment}/{@code decrement} 的计数器在 {@code get} 时返回 {@code Integer}
 *       （与原 Redis + JSON 序列化行为一致，便于调用方直接强转）；</li>
 *   <li>hash 操作以 {@link ConcurrentHashMap} 作为 value 存储；</li>
 *   <li>{@code emptyAll} 对应清空整个缓存。</li>
 * </ul>
 *
 * <p><b>注意</b>：本地缓存为进程内内存，<b>重启后数据丢失</b>，仅适用于单实例部署。
 *
 * Copyright (c) 人人开源 All rights reserved.
 * Website: https://www.renren.io
 */
@Component
public class RedisUtils {
    /**
     * 默认过期时长为24小时，单位：秒
     */
    public final static long DEFAULT_EXPIRE = 60 * 60 * 24L;
    /**
     * 过期时长为1小时，单位：秒
     */
    public final static long HOUR_ONE_EXPIRE = (long) 60 * 60;
    /**
     * 过期时长为6小时，单位：秒
     */
    public final static long HOUR_SIX_EXPIRE = 60 * 60 * 6L;
    /**
     * 不设置过期时长
     */
    public final static long NOT_EXPIRE = -1L;

    /**
     * 永不过期的时间戳哨兵值
     */
    private static final long NEVER_EXPIRE = Long.MAX_VALUE;

    /**
     * 缓存条目：持有真实值与该 key 的过期时间戳（毫秒）。
     * {@link #expireAtMillis} 为 {@link #NEVER_EXPIRE} 时表示永不过期。
     */
    private static final class Entry {
        private final Object value;
        private volatile long expireAtMillis;

        Entry(Object value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }

        boolean neverExpires() {
            return expireAtMillis == NEVER_EXPIRE;
        }
    }

    /**
     * 将过期秒数转换为绝对过期时间戳（毫秒）。
     */
    private static long toExpireAt(long expireSeconds) {
        return expireSeconds == NOT_EXPIRE ? NEVER_EXPIRE
                : System.currentTimeMillis() + expireSeconds * 1000L;
    }

    /**
     * 判断条目是否已过期（针对 {@code asMap().compute} 场景的兜底检查）。
     */
    private static boolean isExpired(Entry entry) {
        return !entry.neverExpires() && System.currentTimeMillis() > entry.expireAtMillis;
    }

    /**
     * 基于 {@link Entry#expireAtMillis} 的逐 key 过期策略，让 Caffeine 主动回收过期条目。
     */
    private static final Expiry<String, Entry> EXPIRY = new Expiry<>() {
        @Override
        public long expireAfterCreate(String key, Entry entry, long currentTime) {
            return remainingNanos(entry);
        }

        @Override
        public long expireAfterUpdate(String key, Entry entry, long currentTime, long currentDuration) {
            return remainingNanos(entry);
        }

        @Override
        public long expireAfterRead(String key, Entry entry, long currentTime, long currentDuration) {
            // 读操作不续期，保持剩余时长不变
            return currentDuration;
        }

        private long remainingNanos(Entry entry) {
            if (entry.neverExpires()) {
                return Long.MAX_VALUE;
            }
            long remaining = entry.expireAtMillis - System.currentTimeMillis();
            return remaining > 0 ? TimeUnit.MILLISECONDS.toNanos(remaining) : 0L;
        }
    };

    private final Cache<String, Entry> cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfter(EXPIRY)
            .build();

    public Long increment(String key, long expire) {
        long[] result = new long[1];
        cache.asMap().compute(key, (k, existing) -> {
            long current = 0L;
            if (existing != null && !isExpired(existing) && existing.value instanceof Number) {
                current = ((Number) existing.value).longValue();
            }
            long next = current + 1L;
            result[0] = next;
            long expireAt;
            if (expire != NOT_EXPIRE) {
                expireAt = toExpireAt(expire);
            } else if (existing != null && !isExpired(existing)) {
                // 未指定过期时保持原 TTL，对齐原 Redis 行为
                expireAt = existing.expireAtMillis;
            } else {
                // 新建且未指定过期 → 永不过期（对齐原 Redis INCR 无 TTL 行为）
                expireAt = NEVER_EXPIRE;
            }
            return new Entry(boxCounter(next), expireAt);
        });
        return result[0];
    }

    public Long increment(String key) {
        // 不指定过期 → 不改动 TTL
        long[] result = new long[1];
        cache.asMap().compute(key, (k, existing) -> {
            long current = 0L;
            if (existing != null && !isExpired(existing) && existing.value instanceof Number) {
                current = ((Number) existing.value).longValue();
            }
            long next = current + 1L;
            result[0] = next;
            long expireAt = (existing != null && !isExpired(existing)) ? existing.expireAtMillis : NEVER_EXPIRE;
            return new Entry(boxCounter(next), expireAt);
        });
        return result[0];
    }

    public Long decrement(String key) {
        long[] result = new long[1];
        cache.asMap().compute(key, (k, existing) -> {
            long current = 0L;
            if (existing != null && !isExpired(existing) && existing.value instanceof Number) {
                current = ((Number) existing.value).longValue();
            }
            long next = current - 1L;
            result[0] = next;
            long expireAt = (existing != null && !isExpired(existing)) ? existing.expireAtMillis : NEVER_EXPIRE;
            return new Entry(boxCounter(next), expireAt);
        });
        return result[0];
    }

    /**
     * 计数器值装箱：对齐原 Redis + JSON 序列化行为，int 范围内返回 {@code Integer}，否则 {@code Long}。
     * 这样调用方 {@code (Integer) get(key)} 的强转在常规计数场景下可正常工作。
     */
    private static Number boxCounter(long value) {
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return (int) value;
        }
        return value;
    }

    public void set(String key, Object value, long expire) {
        cache.put(key, new Entry(value, toExpireAt(expire)));
    }

    public void set(String key, Object value) {
        set(key, value, DEFAULT_EXPIRE);
    }

    public Object get(String key, long expire) {
        Entry entry = cache.getIfPresent(key);
        if (entry == null) {
            return null;
        }
        if (expire != NOT_EXPIRE) {
            // 刷新该 key 的 TTL
            cache.put(key, new Entry(entry.value, toExpireAt(expire)));
        }
        return entry.value;
    }

    public Object get(String key) {
        Entry entry = cache.getIfPresent(key);
        return entry == null ? null : entry.value;
    }

    public void delete(String key) {
        cache.invalidate(key);
    }

    public void delete(Collection<String> keys) {
        if (keys != null && !keys.isEmpty()) {
            cache.invalidateAll(keys);
        }
    }

    public Object hGet(String key, String field) {
        Entry entry = cache.getIfPresent(key);
        if (entry == null || !(entry.value instanceof Map)) {
            return null;
        }
        return ((Map<String, Object>) entry.value).get(field);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> hGetAll(String key) {
        Entry entry = cache.getIfPresent(key);
        if (entry == null || !(entry.value instanceof Map)) {
            return Collections.emptyMap();
        }
        return (Map<String, Object>) entry.value;
    }

    public void hMSet(String key, Map<String, Object> map) {
        hMSet(key, map, DEFAULT_EXPIRE);
    }

    public void hMSet(String key, Map<String, Object> map, long expire) {
        cache.asMap().compute(key, (k, existing) -> {
            ConcurrentHashMap<String, Object> target;
            if (existing != null && !isExpired(existing) && existing.value instanceof ConcurrentHashMap) {
                target = (ConcurrentHashMap<String, Object>) existing.value;
            } else {
                target = new ConcurrentHashMap<>();
            }
            if (map != null) {
                target.putAll(map);
            }
            return new Entry(target, toExpireAt(expire));
        });
    }

    public void hSet(String key, String field, Object value) {
        hSet(key, field, value, DEFAULT_EXPIRE);
    }

    @SuppressWarnings("unchecked")
    public void hSet(String key, String field, Object value, long expire) {
        cache.asMap().compute(key, (k, existing) -> {
            ConcurrentHashMap<String, Object> map;
            if (existing != null && !isExpired(existing) && existing.value instanceof ConcurrentHashMap) {
                map = (ConcurrentHashMap<String, Object>) existing.value;
            } else {
                map = new ConcurrentHashMap<>();
            }
            map.put(field, value);
            return new Entry(map, toExpireAt(expire));
        });
    }

    public void expire(String key, long expire) {
        cache.asMap().computeIfPresent(key, (k, existing) -> new Entry(existing.value, toExpireAt(expire)));
    }

    @SuppressWarnings("unchecked")
    public void hDel(String key, Object... fields) {
        cache.asMap().computeIfPresent(key, (k, existing) -> {
            if (existing.value instanceof Map && fields != null) {
                Map<String, Object> map = (Map<String, Object>) existing.value;
                for (Object field : fields) {
                    map.remove(field);
                }
            }
            return existing;
        });
    }

    public void leftPush(String key, Object value) {
        leftPush(key, value, DEFAULT_EXPIRE);
    }

    @SuppressWarnings("unchecked")
    public void leftPush(String key, Object value, long expire) {
        cache.asMap().compute(key, (k, existing) -> {
            Deque<Object> deque;
            if (existing != null && !isExpired(existing) && existing.value instanceof Deque) {
                deque = (Deque<Object>) existing.value;
            } else {
                deque = new ConcurrentLinkedDeque<>();
            }
            deque.addFirst(value);
            return new Entry(deque, toExpireAt(expire));
        });
    }

    @SuppressWarnings("unchecked")
    public Object rightPop(String key) {
        Object[] result = new Object[1];
        cache.asMap().computeIfPresent(key, (k, existing) -> {
            if (existing.value instanceof Deque) {
                result[0] = ((Deque<Object>) existing.value).pollLast();
            }
            return existing;
        });
        return result[0];
    }

    /**
     * 清空所有缓存数据。
     */
    public void emptyAll() {
        cache.invalidateAll();
    }

    /**
     * 获取指定 key 的值，如果值为空，则设置 key 的默认值。
     *
     * @param key             缓存 key
     * @param defaultValue    默认值
     * @param expiresInSecond 过期时间（秒）
     * @return 返回 key 原有的值（不存在时返回 {@code null}，并写入默认值，对齐原 Redis Lua 行为）
     */
    public String getKeyOrCreate(String key, String defaultValue, Long expiresInSecond) {
        String[] oldValue = {null};
        cache.asMap().compute(key, (k, existing) -> {
            if (existing != null && !isExpired(existing)) {
                oldValue[0] = existing.value instanceof String
                        ? (String) existing.value
                        : String.valueOf(existing.value);
                return existing;
            }
            long expireAt = (expiresInSecond != null && expiresInSecond > 0)
                    ? toExpireAt(expiresInSecond)
                    : NEVER_EXPIRE;
            return new Entry(defaultValue, expireAt);
        });
        return oldValue[0];
    }
}
