package com.example.demo.service.imp;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.demo.MQ.thumb.msg.ThumbEvent;
import com.example.demo.common.ErrorCode;
import com.example.demo.constant.ThumbConstant;
import com.example.demo.exception.BusinessException;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 点赞服务实现类
 * Redis为核心，通过Pulsar异步同步数据库
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
    private final StringRedisTemplate stringRedisTemplate;

    private final CacheManager cacheManager;

    @Resource
    private final RedissonClient redissonClient;

    @Resource
    private final PulsarTemplate<ThumbEvent> pulsarTemplate;

    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1.参数校验
        if (doThumbRequest == null || request == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        user loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();
        Long userId = loginUser.getId();

        // 2.分布式锁（按用户ID加锁，不同用户互不影响）
        String lockKey = ThumbConstant.THUMB_LOCK_KEY_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待3秒，锁自动释放时间10秒
            boolean isLocked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new BusinessException(ErrorCode.THUMB_LOCK_ERROR);
            }

            // 4.检查是否已点赞
            Boolean exists = this.hasThumb(blogId, userId);
            if (exists) {
                throw new BusinessException(ErrorCode.ALREADY_THUMBED_ERROR);
            }

            // 5.Redis操作：记录点赞
            String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
            String fieldKey = blogId.toString();
            // 暂时存储一个占位符，真实thumbId由Consumer写入数据库后生成
            stringRedisTemplate.opsForHash().put(hashKey, fieldKey, "1");
            stringRedisTemplate.expire(hashKey, ThumbConstant.CACHE_EXPIRE_DAYS, TimeUnit.DAYS);

            // 6.发送MQ消息（异步同步数据库）
            try {
                ThumbEvent event = ThumbEvent.builder()
                        .userId(userId)
                        .blogId(blogId)
                        .type(ThumbEvent.ThumbType.INCREASE)
                        .localDateTime(LocalDateTime.now())
                        .build();
                pulsarTemplate.send("thumb-topic", event);
                log.info("发送点赞消息成功: userId={}, blogId={}", userId, blogId);
            } catch (Exception e) {
                log.error("发送点赞消息到Pulsar失败: userId={}, blogId={}", userId, blogId, e);
                // 回滚Redis操作
                stringRedisTemplate.opsForHash().delete(hashKey, fieldKey);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "点赞失败，请重试");
            }

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断: userId={}", userId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        } finally {
            // 释放锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        // 1.参数校验
        if (doThumbRequest == null || doThumbRequest.getBlogId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        user loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();
        Long userId = loginUser.getId();

        // 2.分布式锁
        String lockKey = ThumbConstant.THUMB_LOCK_KEY_PREFIX + userId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new BusinessException(ErrorCode.THUMB_LOCK_ERROR);
            }

            // 3.检查点赞记录
            Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
            if (thumbIdObj == null || thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                throw new BusinessException(ErrorCode.NOT_THUMBED_ERROR);
            }

            // 4.Redis操作：删除点赞记录
            String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
            String fieldKey = blogId.toString();
            stringRedisTemplate.opsForHash().delete(hashKey, fieldKey);

            // 如果本地缓存存在，则标记为未点赞
            cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);

            // 5.发送MQ消息（异步同步数据库）
            try {
                ThumbEvent event = ThumbEvent.builder()
                        .userId(userId)
                        .blogId(blogId)
                        .type(ThumbEvent.ThumbType.DECREASE)
                        .localDateTime(LocalDateTime.now())
                        .build();
                pulsarTemplate.send("thumb-topic", event);
                log.info("发送取消点赞消息成功: userId={}, blogId={}", userId, blogId);
            } catch (Exception e) {
                log.error("发送取消点赞消息到Pulsar失败: userId={}, blogId={}", userId, blogId, e);
                // 回滚Redis操作
                stringRedisTemplate.opsForHash().put(hashKey, fieldKey, "1");
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "取消点赞失败，请重试");
            }

            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断: userId={}", userId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        if (blogId == null || userId == null) {
            return false;
        }

        try {
            Object thumbIdObj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX + userId, blogId.toString());
            if (thumbIdObj == null) {
                return false;
            }

            // 处理未点赞标记
            if (thumbIdObj.equals(ThumbConstant.UN_THUMB_CONSTANT)) {
                return false;
            }

            // 只要有值就表示已点赞
            return true;
        } catch (Exception e) {
            log.error("判断点赞状态失败: userId={}, blogId={}", userId, blogId, e);
            return false;
        }
    }
}




