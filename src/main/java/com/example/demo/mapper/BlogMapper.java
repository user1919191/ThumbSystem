package com.example.demo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.demo.model.entity.blog;
import com.example.demo.model.vo.BlogVO;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @author pine
 */
public interface BlogMapper extends BaseMapper<blog> {
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}




