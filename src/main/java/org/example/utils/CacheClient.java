package org.example.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.example.utils.RedisData;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.annotation.Resource;

import static java.lang.Thread.sleep;
import static org.example.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //RedisData对象用于存储过期时间和数据，其中数据可以是各种类型的，不过既然有过期时间，所以都用String来进行存储
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData(LocalDateTime.now().plusSeconds(time), value);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //对于缓存穿透，这里的解决方法是缓存空值，查询到空值时直接返回，缺点是啥都用字符串存储
    public <R,ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, long time, TimeUnit unit) {
        String key = keyPrefix + id;

        //从redis中获取对象
        String json = stringRedisTemplate.opsForValue().get(key);
        //如果redis中获得的对象不为空，那么转换为type对应的类实例直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //如果获得到的对象不是不存在，那么就是空对象，直接返回错误信息
        if (json != null) {
            return null;
        }

        //从数据库中查询是否存在
        R r = dbFallback.apply(id);
        //如果为空，保存空对象，返回错误
        if(r == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        else {
            this.set(key, r, time, unit);
            return r;
        }
    }

    //逻辑过期实现

    public <R, ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从redis中读取缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //如果为可空，直接返回错误
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        if (expireTime.isAfter(LocalDateTime.now())) {
           return r;
        }

        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean getLock = tryLock(lockKey);
        //尝试重建线程，这里是使用了线程池中的线程，所以是异步重建，数据是在下一步return r中直接返回的
        if (getLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 重建缓存
                    this.setWithLogicalExpire(key, newR, time, unit);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
                finally {
                    unlock(lockKey);
                }
            });
        }
        //这里直接返回旧的数据
        return r;
    }

    //使用互斥锁方案解决缓存击穿，相比于逻辑过期，互斥锁是等缓存重建完毕将数据存入redis后，再返回从数据库中查询到的新的数据的。
    public <R, ID> R queryWithMutex(
            String prefixKey, ID id, Class<R> type, Function<ID, R> dbFallBack, Long time, TimeUnit unit) {
        String key = prefixKey + id;
        // 从redis中查询数据
        String json = stringRedisTemplate.opsForValue().get(key);
        // 如果数据不为空，直接返回
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 判断是否为空
        if (json != null) {
            return null;
        }

        String lockKey = LOCK_SHOP_KEY + id;
        // 没有查询到当前数据，使用互斥锁重建缓存
        boolean getLock = tryLock(lockKey);

        R r;

        try {
            if (!getLock) {
                Thread.sleep(50);
                // 重新进行查询
                queryWithMutex(prefixKey, id, type, dbFallBack, time, unit);
            }

            r = dbFallBack.apply(id);
            // 没有查询到，缓存空字符串来应对缓存穿透
            if (r == null) this.set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 否则设置缓存
            this.set(key, r, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }

        return r;
    }

    private boolean tryLock(String lockKey) {
        return BooleanUtil.isTrue(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 10, TimeUnit.SECONDS));
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
