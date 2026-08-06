package com.agentone.skill.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill 注册表
 * 管理所有已注册的 Skill 执行器
 */
@Slf4j
@Component
public class SkillRegistry {

    private final Map<String, SkillExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 注册 Skill 执行器
     */
    public void register(SkillExecutor executor) {
        String skillId = executor.getDescriptor().getId();
        executors.put(skillId, executor);
        log.info("注册 Skill: id={}, name={}", skillId, executor.getDescriptor().getName());
    }

    /**
     * 注销 Skill
     */
    public void unregister(String skillId) {
        executors.remove(skillId);
        log.info("注销 Skill: id={}", skillId);
    }

    /**
     * 获取 Skill 执行器
     */
    public Optional<SkillExecutor> getExecutor(String skillId) {
        return Optional.ofNullable(executors.get(skillId));
    }

    /**
     * 获取所有 Skill 描述
     */
    public List<SkillDescriptor> listDescriptors() {
        return executors.values().stream()
                .map(SkillExecutor::getDescriptor)
                .toList();
    }

    /**
     * 检查 Skill 是否存在
     */
    public boolean contains(String skillId) {
        return executors.containsKey(skillId);
    }

    /**
     * 获取 Skill 数量
     */
    public int size() {
        return executors.size();
    }
}
