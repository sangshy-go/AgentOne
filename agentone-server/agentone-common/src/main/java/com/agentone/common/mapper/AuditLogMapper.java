package com.agentone.common.mapper;

import com.agentone.common.audit.AuditLogDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 变更审计日志 Mapper
 * 包名必须落在 *.mapper 下：@MapperScan("com.agentone.**.mapper") 按包路径扫描注册
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {
}
