package com.example.demo.controller;

import com.example.demo.common.BaseResponse;
import com.example.demo.common.ResultUtils;
import com.example.demo.model.entity.user;
import com.example.demo.model.vo.UserVO;
import com.example.demo.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/get/login")
    public BaseResponse<UserVO> getLoginUser(HttpServletRequest request) {
        user loginUser = userService.getLoginUser(request);
        UserVO userVO = userService.getUserVO(loginUser);
        return ResultUtils.success(userVO);
    }

}
