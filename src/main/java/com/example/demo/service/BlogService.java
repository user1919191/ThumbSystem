package com.example.demo.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.demo.model.entity.blog;

import com.example.demo.model.entity.user;
import com.example.demo.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * @author pine
 */
public interface BlogService extends IService<blog> {

    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    BlogVO getBlogVO(blog blog, user loginUser);

    List<BlogVO> getBlogVOList(HttpServletRequest request);


}
