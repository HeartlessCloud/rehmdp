package org.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.example.dto.Result;
import org.example.dto.UserDTO;
import org.example.entity.Follow;
import org.example.mapper.FollowMapper;
import org.example.service.IFollowService;
import org.example.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result isFollow(Long followUserId) {
        // 从用户状态中取得ID
        Long userId = UserHolder.get().getId();
        int count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();

        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollows(Long followUserId) {
        Long userId = UserHolder.get().getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + followUserId;

        // 取得用户关注交集
        Set<String> commonUserSet = stringRedisTemplate.opsForSet().intersect(key1, key2);

        if(commonUserSet == null || commonUserSet.isEmpty()) return Result.ok(Collections.emptyList());

        List<Long> commonUserList = commonUserSet.stream().map(Long::valueOf).collect(Collectors.toList());

        // 按照主键值们将用户列表查出，再将用户列表依此映射为UserDTO列表
        List<UserDTO> UserList = this.listByIds(commonUserList)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(UserList);
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 从用户状态中取得用户ID
        Long userId = UserHolder.get().getId();
        // 组装User的key
        String UserKey = "follows" + followUserId;

        if (!isFollow) {
            // 新建一个关注订单
            Follow follow = new Follow().setUserId(userId).setFollowUserId(followUserId);

            boolean success = this.save(follow);

            if (success) {
                stringRedisTemplate.opsForSet().add(UserKey, String.valueOf(userId));
                return Result.ok();
            }

            return Result.fail("插入失败");
        }

        else {
            boolean success = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));

            if (success) {
                stringRedisTemplate.opsForSet().remove(UserKey, userId);
                return Result.ok();
            }
            else {
                return Result.fail("删除失败了");
            }

        }
    }
}
