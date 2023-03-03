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
public class MsgDTO implements Serializable {
    private String jsonData;
    private String exchange;
    private String routingKey;
}
