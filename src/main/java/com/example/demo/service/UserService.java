package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.model.dto.UserLoginRequest;
import com.example.demo.model.entity.user;
import com.example.demo.model.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户接口
 */
public interface UserService extends IService<user> {

    /**
     * 用户登录
     */
    UserVO userLogin(UserLoginRequest loginRequest, HttpServletRequest request);

    /**
     * 获取当前登录用户
     */
    user getLoginUser(HttpServletRequest request);

    /**
     * 用户登出
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取脱敏用户信息
     */
    UserVO getUserVO(user user);
}
