package com.agentone.knowledge.config;

import com.agentone.common.context.Context;
import com.agentone.common.context.RuntimeContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 *
 * 文档的分块 + 向量化是耗时操作（需分批调用外部 Embedding API），
 * 放在异步线程执行，避免同步阻塞 HTTP 请求导致前端超时。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 文档处理专用线程池。
     * 显式指定核心/最大线程数与队列容量，拒绝策略采用 CallerRunsPolicy：
     * 队列打满时由调用线程兜底执行，保证文档不丢失（最多退化为同步处理）。
     */
    @Bean("documentProcessExecutor")
    public Executor documentProcessExecutor(
            @org.springframework.beans.factory.annotation.Value("${agentone.async.core-pool-size:2}") int corePoolSize,
            @org.springframework.beans.factory.annotation.Value("${agentone.async.max-pool-size:4}") int maxPoolSize,
            @org.springframework.beans.factory.annotation.Value("${agentone.async.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("doc-process-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 多租户上下文传播：WorkspaceInterceptor 依赖 ThreadLocal 中的 workspace_id，
        // 异步线程默认拿不到，会把提交线程（请求线程）的 Context 复制到工作线程，
        // 否则异步线程访问 knowledge_base 表会因 workspace_id 为空而抛异常。
        executor.setTaskDecorator(runnable -> {
            // 提交时（请求线程）捕获上下文
            Context context = RuntimeContext.get();
            return () -> {
                // 执行时记录原上下文：工作线程通常为 null；
                // CallerRunsPolicy 兜底在请求线程执行时，即为请求线程自身上下文
                Context previous = RuntimeContext.get();
                try {
                    if (context != null) {
                        RuntimeContext.set(context);
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        RuntimeContext.set(previous);
                    } else {
                        RuntimeContext.clear();
                    }
                }
            };
        });
        executor.initialize();
        return executor;
    }
}
