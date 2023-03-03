package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.RegexUtils;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.constants.RedisConstants.*;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private IFollowService followService;
    @Resource
    private IUserService userService;
    @Resource
    private FollowMapper followMapper;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService POOL = Executors.newFixedThreadPool(300);

    @Test
    void testSaveShop() {
        //shopService.saveShop2Redis(1L,10L);
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpiration(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void testNextId() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable run = () -> {
            for (int i = 0; i < 100; i++) {
                long order = redisIdWorker.nextId("order");
                System.out.println("order = " + order);
            }
            latch.countDown();
        };
        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            POOL.submit(run);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println(end - start);
    }

    @Test
    void loadShopData() {
        //1.获取全部店铺信息
        List<Shop> shops = shopService.list();
        //2.根据typeId分组
        Map<Long, List<Shop>> map = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //3.批量写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            //3.1获取类型
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            //3.2获取同类型店铺集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            //3.3写入redis
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLoglog() {
        String[] values = new String[10000];
        for (int i = 0; i < 10000000; i++) {
            int j = i % 10000;
            values[j] = "user_" + i;
            if (j == 9999) {
                stringRedisTemplate.opsForHyperLogLog().add("uv1", values);
            }
        }
        Long uv1 = stringRedisTemplate.opsForHyperLogLog().size("uv1");
        System.out.println("count = " + uv1);
    }


    @Test
    void testStrMem(){
        Map<String, String> map = new HashMap<>(1000);
        int count = 1;
        for (int i = 0; i < 1000000; i++) {
            int j = i % 1000;
            String mapKey = "str:" + i;
            String val = UUID.randomUUID().toString(true);
            map.put(mapKey, val);
            if (j == 999) {
                String key = "test:mem:str:" + count;
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(map));
                count++;
                map.clear();
            }
        }
    }

    @Test
    void testHashMem(){
        Map<String, String> map = new HashMap<>(1000);
        int count = 1;
        for (int i = 0; i < 1000000; i++) {
            int j = i % 1000;
            String mapKey = "has:" + i;
            String val = UUID.randomUUID().toString(true);
            map.put(mapKey, val);
            if (j == 999) {
                String key = "test:mem:has:" + count;
                stringRedisTemplate.opsForHash().putAll(key, map);
                count++;
                map.clear();
            }
        }
    }

    @Test
    void testFollowByRedis() {
        String key1 = FOLLOWS_KEY + "1010";
        String key2 = FOLLOWS_KEY + "2";
        long start = System.currentTimeMillis();
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        long end = System.currentTimeMillis();
        System.out.println(end - start + "ms");
        users.forEach(System.out::println);
    }

    @Test
    void testFollowByMySQL() {
        long id1 = 1010;
        long id2 = 2;
        long start = System.currentTimeMillis();
        List<String> list = followMapper.followCommons(id1, id2);
        List<Long> ids = list.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        long end = System.currentTimeMillis();
        System.out.println(end - start + "ms");
        users.forEach(System.out::println);
    }

    @Test
    void batchInToken(){
        for (int i = 1; i <= 999; i++) {
            User user = new User();
            user.setId(10000L + i);
            user.setNickName(i + "号机");
            user.setPhone("13033976" + i);

            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((key, value) -> value.toString()));
            // 5.2.生成随机token
            String token = UUID.randomUUID().toString(true);
            // 5.3.存入redis
            String tokenKey = LOGIN_USER_KEY + token;
            System.out.println(token);
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        }
    }
}