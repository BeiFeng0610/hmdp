package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoLocation;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

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

}