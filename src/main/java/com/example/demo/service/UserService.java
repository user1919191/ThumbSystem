package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.model.entity.user;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户接口
 */

public interface UserService extends IService<user> {
    user getLoginUser(HttpServletRequest request);
}
