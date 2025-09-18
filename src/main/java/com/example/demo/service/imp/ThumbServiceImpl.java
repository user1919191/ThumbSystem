package com.example.demo.service.imp;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.common.ErrorCode;
import com.example.demo.constant.ThumbConstant;
import com.example.demo.exception.ThrowUtils;
import com.example.demo.manager.Cache.CacheManager;
import com.example.demo.mapper.ThumbMapper;
import com.example.demo.model.dto.DoThumbRequest;
import com.example.demo.model.entity.thumb;
import com.example.demo.model.entity.user;
import com.example.demo.service.BlogService;
import com.example.demo.service.ThumbService;
import com.example.demo.service.UserService;
import com.example.demo.model.entity.blog;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 *
 */
@Service("thumbServiceLocalCache")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, thumb>
        implements ThumbService {

    @Resource
    private final UserService userService;

    @Resource
    private final BlogService blogService;

    @Resource
    private final TransactionTemplate transactionTemplate;

    @Resource
    private final StringRedisTemplate stringRedisTemplate;

    private final CacheManager cacheManager;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1.参数校验
        ThrowUtils.throwIf(doThumbRequest == null || request == null, ErrorCode.PARAMS_ERROR);

        user loginUser = userService.getLoginUser(request);

        //2.业务加锁
        synchronized (StrUtil.toString(loginUser.getId()).intern()) {
            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                Boolean exists = this.hasThumb(blogId, loginUser.getId());
                if (exists) {
                    throw new RuntimeException("用户已点赞");
                }

                boolean update = blogService.lambdaUpdate()
                        .eq(blog::getId, blogId)
                        .setSql("thumbCount = thumbCount + 1")
                        .update();

                thumb thumb = new thumb();
                thumb.setUserId(loginUser.getId());
                thumb.setBlogId(blogId);
                thumb.setCreateTime(new DateTime());
                boolean success = update && this.save(thumb);

                // 点赞记录存入 Redis
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    stringRedisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId.toString());
                    cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);
                }
                // 更新成功才执行
                return success;
            });
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new RuntimeException("参数错误");
        }
        user loginUser = userService.getLoginUser(request);
        // 加锁
        synchronized (StrUtil.toString(loginUser.getId()).intern()) {

            // 编程式事务
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();
                Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId(), blogId.toString());
                if (thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                    throw new RuntimeException("用户未点赞");
                }
                boolean update = blogService.lambdaUpdate()
                        .eq(blog::getId, blogId)
                        .setSql("thumbCount = thumbCount - 1")
                        .update();

                if (thumbIdObj == null) {
                    return false;
                }
                Long thumbId = null;
                try {
                    thumbId = Long.valueOf(thumbIdObj.toString());
                } catch (NumberFormatException e) {
                    log.error("Failed to convert thumb ID to Long: " + thumbIdObj, e);
                }


                boolean success = update && this.removeById(thumbId);

                // 点赞记录从 Redis 删除
                if (success) {
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    stringRedisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }
                return success;
            });
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
        if (thumbIdObj == null) {
            return false;
        }
            Long thumbId = Long.valueOf(thumbIdObj.toString());
            return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }
}




