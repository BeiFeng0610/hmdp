package com.hmdp.receiver;

import cn.hutool.json.JSONUtil;
import com.hmdp.constants.MQConstants;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

/**
 * @author 16116
 */
@Component
public class SeckillOrderReceiver implements RabbitBaseConsumer {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MQConstants.SECKILL_ORDER_CREATE_QUEUE, durable = "true", autoDelete = "false"),
            exchange = @Exchange(value = MQConstants.SECKILL_ORDER_CREATE_FANOUT_EXCHANGE, type = ExchangeTypes.FANOUT),
            key = ""
    ))
    public void process(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            doExecute(new String(message.getBody()));
            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, true);
        }

    }

    @Override
    public void doExecute(String msg) {
        VoucherOrder order = JSONUtil.toBean(msg, VoucherOrder.class);
        voucherOrderService.handlerVoucherOrder(order);
    }

    @Override
    public String getExchange() {
        return MQConstants.SECKILL_ORDER_CREATE_FANOUT_EXCHANGE;
    }
}
