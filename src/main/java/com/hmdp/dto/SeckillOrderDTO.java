package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * @author 16116
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderDTO implements Serializable {
    private long id;
    private long voucherId;
    private long userId;
}
