package com.example.demo.service.imp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.constant.UserConstant;
import com.example.demo.mapper.UserMapper;
import com.example.demo.model.entity.user;
import com.example.demo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 用户实现类
 */

@Service
public class UserServiceImp extends ServiceImpl<UserMapper, user> implements UserService {

    @Override
    public user getLoginUser(HttpServletRequest request) {
        return (user) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }
}
