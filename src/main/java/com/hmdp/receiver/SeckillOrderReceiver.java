package com.hmdp.receiver;

import cn.hutool.json.JSONUtil;
import com.hmdp.constants.SeckillOrderMQConstants;
import com.hmdp.dto.SeckillOrderDTO;
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
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author 16116
 */
@Component
public class SeckillOrderReceiver {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = SeckillOrderMQConstants.SECKILL_ORDER_CREATE_QUEUE, durable = "true", autoDelete = "false"),
            exchange = @Exchange(value = SeckillOrderMQConstants.SECKILL_ORDER_CREATE_FANOUT_EXCHANGE, type = ExchangeTypes.FANOUT),
            key = ""
    ))
    public void process(Message message, Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            VoucherOrder order = JSONUtil.toBean(new String(message.getBody()), VoucherOrder.class);
            voucherOrderService.handlerVoucherOrder(order);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            channel.basicNack(tag, false, true);
        }

    }
}
