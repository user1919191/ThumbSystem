package com.example.demo.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象(脱敏)
 */
@Data
public class UserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 姓名
     */
    private String username;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 职业
     */
    private String profession;

    /**
     * 账号
     */
    private String account;

    /**
     * 性别 0-女 1-男
     */
    private Integer gender;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 擅长领域
     */
    private String expertise;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}
