package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author borei
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    /**
     * 查询热门文章
     *
     * @param current
     * @return
     */
    Result queryHotBlog(Integer current);

    /**
     * 根据id查询文章
     *
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 文章点赞
     *
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 查询点赞排行
     *
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 保存笔记
     *
     * @param blog
     * @return
     */
    Result saveBlog(Blog blog);

    /**
     * 查询关注的文章列表
     * @param max
     * @param offset
     * @return
     */
    Result queryBlogsOfFollow(Long max, Integer offset);
}
