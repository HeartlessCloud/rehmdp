package org.example.utils;

import org.example.dto.UserDTO;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO userDTO = UserHolder.get();

        if(userDTO == null) {
            response.setStatus(401);
            return false;
        }

        return true;
    }
}

/*
总结：这个登录拦截器实际上是补全上一个拦截器的逻辑，上一个拦截器如果是用户token还在的时候要去访问其他网页，
由于有线程中有保存用户状态的线程变量，所以放行让他去访问。
同时对于用户登录之类不进行拦截，否则没办法访问其他网页。
 */