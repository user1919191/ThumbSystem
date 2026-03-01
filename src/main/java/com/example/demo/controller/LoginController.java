package com.example.demo.controller;

import com.example.demo.model.dto.UserLoginRequest;
import com.example.demo.model.entity.user;
import com.example.demo.model.vo.UserVO;
import com.example.demo.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 登录控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class LoginController {

    @Resource
    private UserService userService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public UserVO login(@RequestBody UserLoginRequest loginRequest, HttpServletRequest request) {
        if (loginRequest == null) {
            throw new RuntimeException("请求参数为空");
        }
        return userService.userLogin(loginRequest, request);
    }

    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public Boolean logout(HttpServletRequest request) {
        if (request == null) {
            throw new RuntimeException("请求参数为空");
        }
        return userService.userLogout(request);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/current")
    public UserVO getCurrentUser(HttpServletRequest request) {
        user loginUser = userService.getLoginUser(request);
        return userService.getUserVO(loginUser);
    }
}
