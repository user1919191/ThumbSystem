package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.model.dto.DoThumbRequest;
import com.example.demo.model.entity.thumb;
import jakarta.servlet.http.HttpServletRequest;

/**
 * @author pine
 */
public interface ThumbService extends IService<thumb> {

    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
//
    Boolean hasThumb(Long blogId, Long userId);
}
