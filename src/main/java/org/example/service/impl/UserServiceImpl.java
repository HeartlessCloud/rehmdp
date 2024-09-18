package org.example.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.LoginFormDTO;
import org.example.dto.UserDTO;
import org.example.mapper.UserMapper;
import org.example.service.IUserService;
import org.example.entity.User;
import org.example.utils.RegexUtils;
import org.example.dto.Result;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.example.utils.RedisConstants.*;
import static org.example.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone) {
        //校验手机号格式正确与否
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }
        //发送验证码，随机生成6位数字
        String code = RandomUtil.randomNumbers(6);
        //将登录业务+数据名
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.info(code);  //这一步实际上应该更换为使用手机发送验证码
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginFormDTO) {
        //校验手机号
        String phone = loginFormDTO.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }

        //取用户中的手机号和缓存(cache)中的手机号
        String code = loginFormDTO.getCode();
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        log.info("cacheCode:" + cacheCode);

        //验证码是否正确
        if(cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误");
        }
        //验证码通过了，查询用户是否存在，不如不存在自动注册
        User user = query().eq("phone", phone).one();
        //如果不存在，自动注册
        if(user == null) {
            user = createUserWithPhone(phone);
        }

        //将用户信息保存到redis中，以判断用户的登录状态
        String token = UUID.randomUUID().toString();

        //将user对象转换为HashMap对象存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        //设置键，用于存储用户信息
        String tokenKey = LOGIN_USER_KEY + token;
        //设置该键值对的过期时间
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //返回token,前端在接收到输入的token后会对其进行存，保存到LocalStorage中，在请求头中携带发送
        log.info("tokenKey:" + tokenKey);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        //由于user是一个实体类，是用于与数据库交互数据的，所以需要设置一个昵称，例如user_abcdadadda，设置的是初始的手机号
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //保存用户，使用到的是IService中的方法
        save(user);
        //这个
        return user;
    }
}
