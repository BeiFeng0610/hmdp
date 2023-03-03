package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.MsgDTO;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * @author 16116
 */
@Component
public class MQClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RabbitTemplate rabbitTemplate;

    public void sendMsg(Object obj, String exchange, String routingKey) {
        String json = JSONUtil.toJsonStr(obj);
        String key = "message:producer:" + UUID.randomUUID().toString(true);
        Message message = MessageBuilder.withBody(json.getBytes())
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setCorrelationId(key).build();

        CorrelationData correlationData = new CorrelationData(key);
        MsgDTO msgDTO = new MsgDTO(json, exchange, routingKey);
        Map<String, String> value = new HashMap<>();
        value.put("message", JSONUtil.toJsonStr(msgDTO));
        value.put("retryCount", "0");
        stringRedisTemplate.opsForHash().putAll(key, value);
        rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
    }

}
