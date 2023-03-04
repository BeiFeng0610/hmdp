package com.hmdp.config;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.MsgDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;


/**
 * @author 16116
 */
@Configuration
@Slf4j
public class RabbitMQConfig {

    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @PostConstruct
    private void initRabbitTemplate() {
        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
            @Override
            public void confirm(CorrelationData data, boolean ack, String cause) {
                String uuid = data.getId();
                if (ack) {
                    stringRedisTemplate.delete(uuid);
                    log.info("消息发送成功，删除redis");
                } else {
                    log.info("消息发送失败，进行重试" + cause);
                    // 使用redis 存储重试次数
                    Long retryCount = stringRedisTemplate.opsForHash().increment(uuid, "retryCount", 1L);
                    if (retryCount <= 5) {
                        try {
                            log.info("第{}次重试", retryCount);
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        String message = (String) stringRedisTemplate.opsForHash().get(uuid, "message");
                        MsgDTO msgDTO = JSONUtil.toBean(message, MsgDTO.class);
                        rabbitTemplate.convertAndSend(msgDTO.getExchange(), msgDTO.getRoutingKey(), msgDTO.getJsonData(), data);
                    }
                }
            }
        });

        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
            @Override
            public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
                log.error("message: " + message);
                rabbitTemplate.convertAndSend(exchange, routingKey, message);
            }
        });
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

}
