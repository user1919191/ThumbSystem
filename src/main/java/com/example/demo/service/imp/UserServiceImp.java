package com.example.demo.service.imp;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.constant.UserConstant;
import com.example.demo.mapper.UserMapper;
import com.example.demo.model.dto.UserLoginRequest;
import com.example.demo.model.entity.user;
import com.example.demo.model.vo.UserVO;
import com.example.demo.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

/**
 * 用户实现类
 */
@Slf4j
@Service
public class UserServiceImp extends ServiceImpl<UserMapper, user> implements UserService {

    /**
     * 盐值，混淆密码
     */
    private static final String SALT = "thumbUser";

    @Override
    public UserVO userLogin(UserLoginRequest loginRequest, HttpServletRequest request) {
        String account = loginRequest.getAccount();
        String password = loginRequest.getPassword();

        // 参数校验
        if (account == null || account.trim().isEmpty()) {
            throw new RuntimeException("账号不能为空");
        }
        if (password == null || password.trim().isEmpty()) {
            throw new RuntimeException("密码不能为空");
        }

        // 加密密码
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + password).getBytes());

        // 查询用户
        QueryWrapper<user> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        queryWrapper.eq("password", encryptPassword);
        user user = this.getOne(queryWrapper);

        if (user == null) {
            log.info("user login failed, account: {}", account);
            throw new RuntimeException("账号或密码错误");
        }

        // 将用户信息存入Session
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user);
        log.info("user login success, userId: {}, sessionId: {}", user.getId(), request.getSession().getId());

        return getUserVO(user);
    }

    @Override
    public user getLoginUser(HttpServletRequest request) {
        user currentUser = (user) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        if (currentUser == null || currentUser.getId() == null) {
            throw new RuntimeException("未登录");
        }
        // 从数据库查询最新用户信息
        user user = this.getById(currentUser.getId());
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return user;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        if (request.getSession().getAttribute(UserConstant.LOGIN_USER) == null) {
            throw new RuntimeException("未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.LOGIN_USER);
        return true;
    }

    @Override
    public UserVO getUserVO(user user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }
}
