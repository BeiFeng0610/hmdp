package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.constants.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.constants.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.constants.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author borei
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 1.根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 2.获取当前页数据
        List<Blog> records = page.getRecords();
        // 3.查询用户及点赞
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //1.查询文章
        Blog blog = baseMapper.selectById(id);
        if (blog == null) {
            return Result.fail("文章不存在");
        }
        //2.查询作者信息
        queryBlogUser(blog);
        //3.查询是否点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    /**
     * 查询是否点赞过
     *
     * @param blog
     */
    private void isBlogLiked(Blog blog) {
        //1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        Long userId = user.getId();
        //2.判断是否点过赞
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        //1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        //2.判断是否点过赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //3.没点就+1
            //3.1.更新数据库
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //3.2.更新redis
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, String.valueOf(userId), System.currentTimeMillis());
            }
        } else {
            //4.点过就-1
            //4.1.更新数据库
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //4.2.更新redis
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, String.valueOf(userId));
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //1.查询top5 点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (CollUtil.isEmpty(top5)) {
            return Result.ok(Collections.emptyList());
        }
        //2.解析id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        //3.查询用户 WHERE id IN(5,1) ORDER BY FIELD (id,5,1)
        String idsStr = StrUtil.join(",", ids);
        List<UserDTO> userDTOS = userService.query().in("id", ids).last("ORDER BY FIELD (id," + idsStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        //4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryBlogsOfFollow(Long max, Integer offset) {
        //1.获取用户
        Long userId = UserHolder.getUser().getId();

        //2.查询收件箱列表
        String key = FEED_KEY + userId;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        if (CollUtil.isEmpty(typedTuples)) {
            return Result.ok();
        }
        //3.解析 获取：blogId nextMin offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long nextMin = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //3.1.获取blogId
            ids.add(Long.valueOf(typedTuple.getValue()));
            //3.2.获取nextMin
            long next = typedTuple.getScore().longValue();
            //3.3.获取 os
            if (next == nextMin) {
                os++;
            } else {
                nextMin = next;
                os = 1;
            }
        }
        //4.查询数据
        List<Blog> blogs = list(new LambdaQueryWrapper<Blog>()
                .in(Blog::getId, ids)
                .orderByDesc(Blog::getCreateTime));

        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }
        //5.封装数据
        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setMinTime(nextMin);
        r.setOffset(os);
        //6.返回
        return Result.ok(r);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // 2.保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("保存笔记失败");
        }
        // 3.查询所有粉丝
        List<Follow> follows = followService.list(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, userId));
        // 4.推送笔记
        follows.forEach(follow -> {
            //4.1.获取key
            String key = FEED_KEY + follow.getUserId();
            //4.2.推送
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), blog.getId());
        });
        // 5.返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查询文章相关用户
     *
     * @param blog
     */
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}