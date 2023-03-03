package com.hmdp.service.impl;

import cn.hutool.core.collection.CollStreamUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.constants.RedisConstants.CACHE_SHOP_TYPE_LIST_KEY;
import static com.hmdp.constants.RedisConstants.CACHE_SHOP_TYPE_LIST_TTL;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author borei
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        //1.查询redis是否有数据
        String key = CACHE_SHOP_TYPE_LIST_KEY;
        List<String> jsonList = stringRedisTemplate.opsForList().range(key, 0, -1);
        //2.有直接返回
        if (jsonList != null && !jsonList.isEmpty()) {
            List<ShopType> typeList = CollStreamUtil.toList(jsonList, json -> JSONUtil.toBean(json, ShopType.class));
            return Result.ok(typeList);
        }
        //3.没有就去数据库查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        jsonList = CollStreamUtil.toList(typeList, JSONUtil::toJsonStr);
        //4.存入redis
        stringRedisTemplate.opsForList().rightPushAll(key, jsonList);
        stringRedisTemplate.expire(key, CACHE_SHOP_TYPE_LIST_TTL, MINUTES);
        //5.返回
        return Result.ok(typeList);
    }
}
