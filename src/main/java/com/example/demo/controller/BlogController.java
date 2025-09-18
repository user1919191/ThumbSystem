package com.example.demo.controller;

import com.example.demo.common.BaseResponse;
import com.example.demo.common.ErrorCode;
import com.example.demo.common.ResultUtils;
import com.example.demo.exception.ThrowUtils;
import com.example.demo.model.vo.BlogVO;
import com.example.demo.service.BlogService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("blog")
public class BlogController {

    @Resource
    private BlogService blogService;

    @PostMapping("/get")
    public BaseResponse<BlogVO> getBlogById(long blogId, HttpServletRequest request){
        //1.参数校验
        ThrowUtils.throwIf(blogId <= 0, ErrorCode.PARAMS_ERROR, "博客id不能为空");

        BlogVO blogVOById = blogService.getBlogVOById(blogId, request);
        return ResultUtils.success(blogVOById);
    }

    @PostMapping("/list")
    public BaseResponse<List<BlogVO>> getBlogList(HttpServletRequest request){
        List<BlogVO> blogVOList = blogService.getBlogVOList(request);
        return ResultUtils.success(blogVOList);
    }
}
