package com.hmdp.entity.enums;

import lombok.Getter;

/**
 * @author 16116
 */
@Getter
public enum SeckillResultEnum {
    INSUFFICIENT(1, "库存不足"),
    DOUBLE_BOOKING(2, "不可重复购买"),
    NOT_STARTED(3, "秒杀未开始"),
    OVER(4,"秒杀已结束")
    ;

    private Integer result;
    private String message;

    SeckillResultEnum(int result, String message) {
        this.result = result;
        this.message = message;
    }

    public static String getMessageByResult(Integer status) {
        SeckillResultEnum arrObj[] = SeckillResultEnum.values();
        for (SeckillResultEnum obj : arrObj) {
            if (status.intValue() == obj.getResult().intValue()) {
                return obj.getMessage();
            }
        }
        return "";
    }
}
