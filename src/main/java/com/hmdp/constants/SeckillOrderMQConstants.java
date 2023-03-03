package com.hmdp.constants;

/**
 * @author 16116
 */
public abstract class SeckillOrderMQConstants {
    /**
     * 生成秒杀订单交换机
     */
    public static final String SECKILL_ORDER_CREATE_FANOUT_EXCHANGE = "seckill.order.create.fanout.exchange";
    /**
     * 生成秒杀订单队列
     */
    public static final String SECKILL_ORDER_CREATE_QUEUE = "seckill.order.create.queue";
}
