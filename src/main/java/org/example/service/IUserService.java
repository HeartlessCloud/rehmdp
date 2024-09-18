package org.example.service;

import org.example.dto.LoginFormDTO;
import org.example.dto.Result;

public interface IUserService {
    public Result sendCode(String phone);

    public Result login(LoginFormDTO loginFormDTO);
}
