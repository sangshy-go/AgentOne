package com.agentone.skill.config;

import com.agentone.skill.core.SkillExecutor;
import com.agentone.skill.core.SkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Skill 自动注册配置
 * 应用启动时自动将所有 SkillExecutor Bean 注册到 SkillRegistry
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SkillAutoRegisterConfig implements ApplicationRunner {

    private final SkillRegistry skillRegistry;
    private final List<SkillExecutor> executors;

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始注册 Skill，共 {} 个", executors.size());
        for (SkillExecutor executor : executors) {
            skillRegistry.register(executor);
        }
        log.info("Skill 注册完成，Registry 中 {} 个 Skill", skillRegistry.size());
    }
}
