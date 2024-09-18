package org.example.utils;

import org.example.dto.UserDTO;

//用于保存用户状态信息的线程变量，并进行封装
public class UserHolder {
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void set(UserDTO user) {tl.set(user);}

    public static UserDTO get() {return tl.get();}

    public static void remove() {tl.remove();}
}