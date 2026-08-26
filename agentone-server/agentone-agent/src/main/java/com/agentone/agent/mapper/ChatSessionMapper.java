package com.agentone.agent.mapper;

import com.agentone.agent.entity.ChatSessionDO;
import com.agentone.agent.vo.DailyChatStatVO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSessionDO> {

    /**
     * 指定时间之后的会话数（租户拦截器自动注入 workspace_id 条件）
     */
    @Select("SELECT COUNT(*) FROM chat_session WHERE created_at >= #{since}")
    Long countSessionsSince(@Param("since") LocalDateTime since);

    /**
     * 指定时间之后的活跃用户数（去重 user_id）
     */
    @Select("SELECT COUNT(DISTINCT user_id) FROM chat_session WHERE created_at >= #{since}")
    Long countActiveUsersSince(@Param("since") LocalDateTime since);

    /**
     * 按天聚合的会话数（趋势图数据源，缺日由服务层补 0）
     */
    @Select("SELECT to_char(date_trunc('day', created_at), 'YYYY-MM-DD') AS stat_date, "
            + "COUNT(*) AS stat_count "
            + "FROM chat_session WHERE created_at >= #{since} "
            + "GROUP BY 1 ORDER BY 1")
    List<DailyChatStatVO> countSessionsByDay(@Param("since") LocalDateTime since);
}
