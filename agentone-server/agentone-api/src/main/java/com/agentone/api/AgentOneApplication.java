package com.agentone.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentOne 启动入口
 */
@SpringBootApplication(scanBasePackages = "com.agentone")
@MapperScan("com.agentone.**.mapper")
public class AgentOneApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOneApplication.class, args);
    }
}
