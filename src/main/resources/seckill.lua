-- 1.入参
-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]
-- 当前时间（秒）
local nowTime = ARGV[4]

-- 2.数据key
local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId
local beginTime = 'seckill:begintime:' .. voucherId
local endTime = 'seckill:endtime:' .. voucherId

-- 3.主逻辑
-- 3.判断当前时间
if (tonumber(redis.call('get',beginTime)) > tonumber(nowTime)) then
    -- 秒杀还未开始
    return 3
end
if (tonumber(redis.call('get',endTime)) < tonumber(nowTime)) then
    -- 秒杀已经结束
    return 4
end

-- 3.1.判断库存
if (tonumber(redis.call('get', stockKey)) <= 0) then
    -- 3.2库存不足
    return 1
end

-- 3.3.判断是否下过单
if (redis.call("sismember", orderKey, userId) == 1) then
    -- 3.4.已下过单
    return 2
end

-- 3.5.扣减库存
redis.call("incrby", stockKey, -1)
-- 3.6.下单（保存用户id）
redis.call("sadd", orderKey, userId)
-- 3.7.添加消息 xadd stream:orders * k1 v1 k2 v2 ...
redis.call("xadd", "stream:orders", "*", "userId", userId, "voucherId", voucherId, "id", orderId)
return 0
