package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
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

    @Override
    public Result queryShopById(Long id) {
        //1.防止穿透
        //Shop shop = queryWithPassThrough(id);
        //互斥锁防止击穿
        Shop shop = queryWithMutex(id);
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        //7.返回
        return Result.ok(shop);
    }

    /**
     * 防止击穿（互斥）
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id){
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
        try {
            if (!tryLock(lockKey)) {
                TimeUnit.MILLISECONDS.sleep(50);
                return queryWithMutex(id);
            }
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
            TimeUnit.MILLISECONDS.sleep(1000);
            //4.5.不存在就报错
            if (shop == null) {
                //4.5.1.存储null值
                stringRedisTemplate.opsForValue().set(shopKey, "", CACHE_NULL_TTL, MINUTES);
                return null;
            }
            //4.6.存在就存入redis
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }
        //5.返回
        return shop;
    }

    /**
     * 防止穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id){
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
    }


    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 解锁
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
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

}
