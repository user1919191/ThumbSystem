package com.example.demo.MQ.thumb.msg;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 点赞事件
 */
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
public class ThumbEvent {

    public ThumbEvent(Long userId, Long blogId, ThumbEvent.ThumbType thumbType, LocalDateTime timestamp) {
        this.userId = userId;
        this.blogId = blogId;
        this.type = thumbType;
        this.localDateTime = timestamp;
    }

    private String id;

    private Long userId;

    private Long blogId;

    private ThumbType type;

    private LocalDateTime localDateTime;

    public enum ThumbType {
        INCREASE,
        DECREASE
    }
}
