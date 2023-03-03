package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.constants.RedisConstants.*;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * @author 16116
 */
@Slf4j
@Component
public class CacheClient {

    /**
     * 线程池
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newSingleThreadExecutor();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 把所有 value 转为 String 存入redis，不设置过期时间
     *
     * @param key
     * @param value
     */
    public void set(String key, Object value) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), -1, SECONDS);
    }

    /**
     * 把所有 value 转为 String 存入redis，设置过期时间
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 把所有 value 封装为 RedisData 并转为 String 存入 redis，设置逻辑过期时间。
     *
     * @param key
     * @param value
     * @param time
     * @param unit
     */
    public void setWithLogicalExpiration(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 防止穿透
     *
     * @param keyPrefix  key前缀
     * @param id         逻辑id
     * @param type       数据类型
     * @param dbFallback 查询函数
     * @param time       时间
     * @param unit       单位
     * @param <R>        返回类型
     * @param <ID>       逻辑id类型
     * @return 返回查询数据
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //1.从redis查询
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3.存在直接返回
            return JSONUtil.toBean(json, type);
        }
        // 判断是否为空数据
        if (json != null) {
            return null;
        }

        //4.不存在去数据库查询
        R r = dbFallback.apply(id);
        //5.不存在就报错
        if (r == null) {
            //5.1.存储null值
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, MINUTES);
            return null;
        }
        //6.存在就存入redis
        this.set(key, r, time, unit);
        //7.返回
        return r;
    }

    /**
     * 防止击穿（逻辑过期）
     *
     * @param keyPrefix  key前缀
     * @param id         逻辑id
     * @param type       查询类型
     * @param dbFallback 调用数据库函数
     * @param time       时间
     * @param unit       单位
     * @param <R>        结果类型
     * @param <ID>       逻辑id类型
     * @return 查询内容
     */
    public <R, ID> R queryWithLogicalExpiration(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        //1.从redis查询
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //3.不存在直接返回
            return null;
        }
        //4.命中，反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1.未过期直接返回
            return r;
        }
        //6.过期重建
        //6.1.获取互斥锁
        String lockKey = LOCK_LOGICAL + key;
        boolean lock = tryLock(lockKey);
        //6.2.判断是否获取成功
        if (lock) {
            //6.3.再次判断是否过期
            //6.3.1.从redis查询
            json = stringRedisTemplate.opsForValue().get(key);
            //6.3.2.反序列化
            redisData = JSONUtil.toBean(json, RedisData.class);
            r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
            expireTime = redisData.getExpireTime();
            //6.3.3.判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                //5.1.未过期直接返回
                return r;
            }
            //6.3.开启线程重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    R value = dbFallback.apply(id);
                    //写入redis
                    this.setWithLogicalExpiration(key, value, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4.失败直接返回旧数据
        return r;
    }

    /**
     * 尝试获取锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_TTL, SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 解锁
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
