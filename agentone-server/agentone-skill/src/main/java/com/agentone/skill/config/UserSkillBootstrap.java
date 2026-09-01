package com.agentone.skill.config;

import com.agentone.skill.core.SkillRegistry;
import com.agentone.skill.entity.SkillDO;
import com.agentone.skill.executor.UserSkillExecutors;
import com.agentone.skill.mapper.SkillMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * 用户 Skill 启动加载器（api + prompt 两类）。
 *
 * 应用启动时把 DB 中所有 active 的用户 Skill 注册进 SkillRegistry，
 * 与 SkillAutoRegisterConfig（注册 builtin Bean）一起完成启动装配；
 * 之后对话侧 buildToolkit 即可通过 skillId 找到执行器。
 *
 * 注意：skill 表受租户拦截器保护，启动期没有 RuntimeContext，
 * 因此走 SkillMapper.selectAllActiveUserSkills()（@InterceptorIgnore 显式跳过）。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UserSkillBootstrap implements ApplicationRunner {

    private final SkillMapper skillMapper;
    private final SkillRegistry skillRegistry;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        List<SkillDO> userSkills = skillMapper.selectAllActiveUserSkills();
        int registered = 0;
        for (SkillDO skill : userSkills) {
            try {
                skillRegistry.register(UserSkillExecutors.create(skill, webClient, objectMapper));
                registered++;
            } catch (Exception e) {
                // 单个 Skill 注册失败不影响其他 Skill 与系统启动
                log.warn("用户 Skill 注册失败，已跳过: id={}, name={}, error={}",
                        skill.getId(), skill.getName(), e.getMessage());
            }
        }
        log.info("用户 Skill 启动加载完成: DB 中 {} 个，成功注册 {} 个", userSkills.size(), registered);
    }
}
