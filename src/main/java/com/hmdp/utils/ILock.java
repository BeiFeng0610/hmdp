package com.hmdp.utils;

/**
 * @author 16116
 */
public interface ILock {

    /**
     * 尝试获取锁
     *
     * @param timeoutSec
     * @return
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();

}
