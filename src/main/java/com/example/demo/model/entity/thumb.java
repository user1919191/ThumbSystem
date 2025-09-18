package com.example.demo.model.entity;

import cn.hutool.core.date.DateTime;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
public class thumb {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;


    private Long blogId;

    /**
     * 创建时间
     */
    private DateTime createTime;
}