package com.hmdp.receiver;

public interface RabbitBaseConsumer {
    void doExecute(String msg) throws Exception;
    String getExchange();
}
