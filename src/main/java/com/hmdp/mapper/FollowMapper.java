package com.hmdp.mapper;

import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author borei
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {
    List<String> followCommons(long userid, long id);
}
