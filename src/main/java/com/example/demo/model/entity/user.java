package com.example.demo.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

@Data
public class user {
    /**
     * 用户ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private long id;

    /**
     * 用户名称
     */
    private String username;

}
