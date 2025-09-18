package com.example.demo.controller;

import com.example.demo.common.BaseResponse;
import com.example.demo.common.ErrorCode;
import com.example.demo.common.ResultUtils;
import com.example.demo.constant.UserConstant;
import com.example.demo.exception.ThrowUtils;
import com.example.demo.model.entity.user;
import com.example.demo.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户请求层
 */
@Slf4j
@RequestMapping("/user")
@RestController
public class UserController {

    @Resource
    private UserService userService;

    @PostMapping("/login")
    public BaseResponse<Boolean> loginUser(long id, HttpServletRequest httpServletRequest){
        user userbyId = userService.getById(id);
        httpServletRequest.getSession().setAttribute(UserConstant.LOGIN_USER, userbyId);
        ThrowUtils.throwIf(userbyId == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        return ResultUtils.success(true);
    }

    @GetMapping("/get/login")
    public BaseResponse<user> getLoginUser(HttpServletRequest request) {
        user login = (user) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        return ResultUtils.success(login);
    }

}
