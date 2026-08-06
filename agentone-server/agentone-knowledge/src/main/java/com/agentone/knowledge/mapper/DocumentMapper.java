package com.agentone.knowledge.mapper;

import com.agentone.knowledge.entity.DocumentDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文档 Mapper
 */
@Mapper
public interface DocumentMapper extends BaseMapper<DocumentDO> {
}
