package com.hmdp.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.DEFAULT_PAGE_SIZE;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author borei
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.判断是否需要根据坐标查询
        if (ObjectUtil.isEmpty(x) || ObjectUtil.isEmpty(y)) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //2.计算分页
        int from = (current - 1) * DEFAULT_PAGE_SIZE;
        int end = current * DEFAULT_PAGE_SIZE;
        //3.查询redis
        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(key,
                        GeoReference.fromCoordinate(x, y), new Distance(5000),
                        RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        //4.解析数据
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        if (content.size() <= from) {
            return Result.ok(Collections.emptyList());
        }
        //4.1截取需要的数据
        List<String> ids = new ArrayList<>();
        Map<String, Distance> distanceMap = new HashMap<>();
        content.stream().skip(from).forEach(result -> {
            String shopId = result.getContent().getName();
            ids.add(shopId);
            Distance distance = result.getDistance();
            distanceMap.put(shopId, distance);
        });

        //5.查询数据库
        String idsStr = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD (id," + idsStr + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        //6.返回结果
        return Result.ok(shops);
    }

    @Override
    public Result queryShopById(Long id) {
        //1.防止穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, baseMapper::selectById, CACHE_SHOP_TTL, MINUTES);
        //互斥锁防止击穿
        //Shop shop = queryWithMutex(id);
        //逻辑过期防止击穿
        /*Shop shop = cacheClient
                .queryWithLogicalExpiration(CACHE_SHOP_KEY, id, Shop.class,
                        baseMapper::selectById, CACHE_SHOP_TTL, MINUTES);*/
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        //7.返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("参数错误");
        }
        //1.更新信息
        baseMapper.updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 防止击穿（互斥）
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        //1.从redis查询
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2.判断是否存在
        if (!StrUtil.isBlank(shopJson)) {
            //3.存在直接返回
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 判断是否为空数据
        if (shopJson != null) {
            return null;
        }
        //4.尝试获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        if (!tryLock(lockKey)) {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return queryWithMutex(id);
        }
        try {
            //4.1.再次判断是否有缓存
            shopJson = stringRedisTemplate.opsForValue().get(shopKey);
            //4.2.判断是否存在
            if (!StrUtil.isBlank(shopJson)) {
                //4.3.存在直接返回
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            // 判断是否为空数据
            if (shopJson != null) {
                return null;
            }

            //4.4.不存在去数据库查询
            shop = baseMapper.selectById(id);
            //4.5.不存在就报错
            if (shop == null) {
                //4.5.1.存储null值
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, MINUTES);
                return null;
            }
            //4.6.存在就存入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, MINUTES);
        } finally {
            unLock(lockKey);
        }
        //5.返回
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) {
        //1.查询店铺信息
        Shop shop = baseMapper.selectById(id);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3.存入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 加锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, SECONDS);
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

    /**
     * 防止击穿（逻辑过期）
     * @param id
     * @return
     */
    /*public Shop queryWithLogicalExpiration(Long id){
        //1.从redis查询
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3.不存在直接返回
            return null;
        }
        //4.命中，反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5.判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1未过期
            return shop;
        }
        //6.过期重建
        //6.1.获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean lock = tryLock(lockKey);
        //6.2.判断是否获取成功
        if (lock) {
            //6.3.再次判断是否过期
            //6.3.1.从redis查询
            shopJson = stringRedisTemplate.opsForValue().get(shopKey);
            //6.3.2.反序列化
            redisData = JSONUtil.toBean(shopJson, RedisData.class);
            shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
            expireTime = redisData.getExpireTime();
            //6.3.3.判断是否过期
            if (expireTime.isAfter(LocalDateTime.now())) {
                //5.1未过期
                return shop;
            }
            //6.3.开启线程重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //重建缓存
                    saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        //6.4.失败直接返回旧数据
        return shop;
    }*/

    /**
     * 防止穿透
     * @param id
     * @return
     */
    /*public Shop queryWithPassThrough(Long id){
        //1.从redis查询
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //2.判断是否存在
        if (!StrUtil.isBlank(shopJson)) {
            //3.存在直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        // 判断是否为空数据
        if (shopJson != null) {
            return null;
        }

        //4.不存在去数据库查询
        Shop shop = baseMapper.selectById(id);
        //5.不存在就报错
        if (shop == null) {
            //5.1.存储null值
            stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, MINUTES);
            return null;
        }
        //6.存在就存入redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, MINUTES);
        //7.返回
        return shop;
    }*/
}
