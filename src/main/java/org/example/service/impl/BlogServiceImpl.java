package org.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.dto.Result;
import org.example.dto.UserDTO;
import org.example.entity.Blog;
import org.example.entity.User;
import org.example.mapper.BlogMapper;
import org.example.service.IBlogService;
import org.example.service.IUserService;
import org.example.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.example.utils.RedisConstants.BLOG_LIKED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result likeBlog(Long id) {
        // 拿到用户状态值
        UserDTO userDTO = UserHolder.get();
        Long userId = userDTO.getId();

        // 求KEY
        String key = BLOG_LIKED_KEY + id;

        // 判断该用户是否点赞过，如果点赞过，则取消点赞；如果没点赞，点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }

        else {
            boolean success = update().setSql("liked = liked - 1").eq("id", id).update();
            if (success) stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }

        return Result.ok();
    }

    // 查询前五个点赞的，这个传入的id是博客的ID
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        if(top5 == null || top5.isEmpty()) return Result.ok(Collections.emptyList());

        // 先将
        List<Long> ids = top5.stream().map(Long::valueOf).toList();

        String idsStr = StrUtil.join(",", ids);

        List<UserDTO> userDTOs = query()
                .in("id", ids).last("ORDER BY FIELD(id, " + idsStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .toList();

        return Result.ok(userDTOs);
    }

}
