package org.example.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import org.example.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.example.utils.RedisConstants.LOGIN_USER_KEY;
import static org.example.utils.RedisConstants.LOGIN_USER_TTL;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception{
        //判断请求头中是否存在token
        String token = request.getHeader("authorization");
        if(StrUtil.isBlank(token)) {
            return true;
        }

        //基于TOKEN获取redis中的用户
        String key = LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        //判断用户是否存在，因为可能缓存了空的键值对来应对缓存穿透
        if(userMap == null) {
            return true;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //UserHolder用于存储用户状态，这种可以横跨多个方法调用的变量一般称之为上下文
        UserHolder.set(userDTO);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    //在整个线程执行完毕时，将线程中保存的用户状态删除
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.remove();
    }
}


/*总结，当用户发送的请求头中不存在authorization时，即代表不存在token，和用户的token为空时，直接放行。
逻辑就是用户不存在的话直接放行，用户存在的话(使用请求头中的authorize进行判断的)，那么在刷新完token有效期，
并且将用户状态保存在线程变量中后通行，也就是，通行是一定会通行的。
拦截器的作用就是给已经存在的用户来刷新有效期而已。
在用户执行完操作后，也即线程任务结束时，将用户状态删除，即删除线程变量ThreadLocal。
*/
