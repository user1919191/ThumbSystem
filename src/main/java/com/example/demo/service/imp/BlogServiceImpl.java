package com.example.demo.service.imp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.common.ErrorCode;
import com.example.demo.exception.ThrowUtils;
import com.example.demo.mapper.BlogMapper;
import com.example.demo.model.entity.blog;
import com.example.demo.model.entity.thumb;
import com.example.demo.model.entity.user;
import com.example.demo.model.vo.BlogVO;
import com.example.demo.service.BlogService;
import com.example.demo.service.ThumbService;
import com.example.demo.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, blog> implements BlogService {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;

    @Resource
    private Cache<String,blog> blogCache;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        //1.参数校验
        ThrowUtils.throwIf(blogId <= 0, ErrorCode.NOT_FOUND_ERROR, "博客id不能小于等于0");
        //2.查询本地缓存
        blog blogLocalCache = blogCache.getIfPresent("blog:" + blogId);
        if(!(ObjectUtil.isNull(blogLocalCache) || ObjectUtil.isEmpty(blogLocalCache))){
            return this.getBlogVO(blogLocalCache, userService.getLoginUser(request));
        }
        //Todo 是否需要Redis
        //3.查询数据库
        blog byId = this.getById(blogId);
        user loginUser = userService.getLoginUser(request);
        return this.getBlogVO(byId, loginUser);
    }

    @Override
    public BlogVO getBlogVO(blog blog, user loginUser) {
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);

        if (loginUser != null) {
           return blogVO;
        }

        thumb thumbOne = thumbService.lambdaQuery().eq(thumb::getUserId, loginUser.getId())
                .eq(thumb::getBlogId, blog.getId()).one();
        blogVO.setHasThumb(ObjUtil.isNotNull(thumbOne));
        return blogVO;
    }

    @Override
    public List<BlogVO> getBlogVOList(HttpServletRequest request){
        List<blog> blogLists = this.list();
        List<BlogVO> blogVOList = blogLists.stream().map
                (blog -> this.getBlogVO(blog, userService.getLoginUser(request)))
                .collect(Collectors.toUnmodifiableList());
        return blogVOList;
    }


}




