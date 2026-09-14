package com.interview.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 应用通用线程池（自定义，供各处复用）。
 *
 * <p>目前用于：用户投稿的图片搬运 + 正文渲染等耗时操作（提交接口先落库立刻返回，重活异步做）。
 * 以后有其它耗时任务（批量导入、统计汇总等）直接用 {@code @Async("appTaskExecutor")} 即可复用，
 * 参数在配置文件的 {@code app.async.*} 里调整，不用改代码。
 *
 * <p>线程池策略：
 * <ul>
 *   <li>核心线程常驻，忙时扩到 max，超过后进队列（{@code queue-capacity}）；</li>
 *   <li>队列也满时用 {@link ThreadPoolExecutor.CallerRunsPolicy}——由提交任务的线程自己执行，
 *       宁可慢一点也不会丢任务（搬图不能丢）；</li>
 *   <li>线程名统一前缀 {@code app-async-}，日志排查方便。</li>
 * </ul>
 */
@Slf4j
@EnableAsync
@Configuration
public class AsyncConfig implements AsyncConfigurer {

    @Value("${app.async.core-pool-size:2}")
    private int corePoolSize;

    @Value("${app.async.max-pool-size:4}")
    private int maxPoolSize;

    @Value("${app.async.queue-capacity:50}")
    private int queueCapacity;

    @Value("${app.async.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    /** 通用任务线程池：别处直接 {@code @Async("appTaskExecutor")} 复用。 */
    @Bean("appTaskExecutor")
    public ThreadPoolTaskExecutor appTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(Math.max(maxPoolSize, corePoolSize));
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(keepAliveSeconds);
        executor.setThreadNamePrefix("app-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 关闭应用时等待正在跑的图片搬运任务收尾（最多 30 秒），避免半途中断留下"处理中"
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("通用线程池 appTaskExecutor 已初始化: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    /** 不指定名字的 {@code @Async} 默认用这个池。 */
    @Override
    public Executor getAsyncExecutor() {
        return appTaskExecutor();
    }

    /** 异步任务里抛出的异常统一打日志，避免静默失败。 */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("异步任务执行失败: {}.{}", method.getDeclaringClass().getSimpleName(),
                        method.getName(), ex);
    }
}
