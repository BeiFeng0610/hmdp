package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author borei
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    /**
     * 关注取关
     *
     * @param followUserId
     * @param isFollow
     * @return
     */
    Result follow(Long followUserId, Boolean isFollow);

    /**
     * 查询是否关注
     *
     * @param followUserId
     * @return
     */
    Result isFollow(Long followUserId);

    /**
     * 共同关注
     *
     * @param id
     * @return
     */
    Result followCommons(Long id);

    /**
     * 查询我的关注
     *
     * @return
     */
    Result myFollows();

    Result testRedis();

    Result testMysql();
}
