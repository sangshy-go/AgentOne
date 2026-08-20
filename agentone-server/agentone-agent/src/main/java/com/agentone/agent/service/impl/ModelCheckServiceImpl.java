package com.agentone.agent.service.impl;

import com.agentone.agent.dto.ModelCheckDTO;
import com.agentone.agent.service.ModelCheckService;
import com.agentone.agent.util.BaseUrlValidator;
import com.agentone.agent.vo.ModelCheckVO;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.Model;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型检测服务实现
 * 使用 AgentScope 的 Model 进行连通性检测
 */
@Slf4j
@Service
public class ModelCheckServiceImpl implements ModelCheckService {

    @Override
    public ModelCheckVO checkModel(ModelCheckDTO dto) {
        long start = System.currentTimeMillis();
        ModelCheckVO vo = new ModelCheckVO();
        vo.setModel(dto.getModel());

        try {
            String baseUrl = dto.getBaseUrl() != null ? dto.getBaseUrl() : "https://api.openai.com";
            // SSRF 防护：拒绝访问内网/回环/链路本地/保留地址（Bug7）
            BaseUrlValidator.validateForSsrf(baseUrl);
            Model tempModel = OpenAIChatModel.builder()
                    .modelName(dto.getModel())
                    .apiKey(dto.getApiKey())
                    .baseUrl(baseUrl)
                    .build();

            Msg testMsg = Msg.builder()
                    .name("user")
                    .role(MsgRole.USER)
                    .content(List.of(TextBlock.builder().text("Hi").build()))
                    .build();

            tempModel.stream(List.of(testMsg), null, null)
                    .blockFirst(java.time.Duration.ofSeconds(15));

            vo.setAvailable(true);
            vo.setMessage("模型可用");
            vo.setLatencyMs(System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("模型检测失败: model={}, error={}", dto.getModel(), e.getMessage());
            vo.setAvailable(false);
            vo.setMessage("检测失败: " + e.getMessage());
            vo.setLatencyMs(System.currentTimeMillis() - start);
        }

        return vo;
    }
}
