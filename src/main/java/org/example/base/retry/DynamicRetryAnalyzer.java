package org.example.base.retry;

import org.example.base.retry.Retryable.RetryOnAssertion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 动态重试分析器 — 根据失败类型智能决策 + 外部参数注入.
 *
 * <h3>与你原有 {@link SmartRetryAnalyzer} 的区别</h3>
 * <ul>
 *   <li>重试次数/延迟从 {@link RetryConfig} 读取，支持命令行 -D 动态配置</li>
 *   <li>支持 {@link Retryable} 注解的方法级覆盖</li>
 *   <li>异常分类：跳过不可重试的异常类型</li>
 *   <li>断言失败可独立控制是否重试</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // testng.xml 中配合 ConditionalRetryTransformer 全局注入
 * <listener class-name="base.ConditionalRetryTransformer"/>
 *
 * // 或直接在测试方法上指定
 * @Test(retryAnalyzer = DynamicRetryAnalyzer.class)
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
public class DynamicRetryAnalyzer implements IRetryAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DynamicRetryAnalyzer.class);

    // ======================== 实例状态 ========================

    private int retryCount = 0;

    /** 懒加载：首次 retry() 时从 @Retryable 注解读取 */
    private Integer methodMaxRetry;
    private Long methodDelayMs;
    private Boolean methodRetryOnAssertion;
    private boolean methodConfigResolved;

    // ======================== 决策核心 ========================

    @Override
    public boolean retry(ITestResult result) {
        // 1. 全局开关
        if (!RetryConfig.isRetryEnabled()) {
            log.debug("⏭️ 全局重试已关闭，跳过: {}", result.getName());
            return false;
        }

        // 2. 懒加载方法级 @Retryable 配置
        resolveMethodConfig(result);

        // 3. 解析生效配置（@Retryable > 全局 > 默认）
        int effectiveMax = resolveMaxRetry();
        long effectiveDelay = resolveDelayMs();
        boolean effectiveRetryOnAssertion = resolveRetryOnAssertion();

        // 4. 次数判断
        if (retryCount >= effectiveMax) {
            log.info("⛔ [{}] 已达最大重试次数 {}/{}",
                    result.getName(), retryCount, effectiveMax);
            return false;
        }

        // 5. 异常分析
        Throwable throwable = result.getThrowable();
        if (throwable != null && !isRetryableException(throwable)) {
            log.warn("🚫 [{}] 不可重试异常: {}",
                    result.getName(), throwable.getClass().getName());
            return false;
        }

        // 6. 断言独立控制
        if (throwable instanceof AssertionError && !effectiveRetryOnAssertion) {
            log.info("⏭️ [{}] 断言失败，已关闭断言重试", result.getName());
            return false;
        }

        retryCount++;

        // 7. 延迟等待
        if (effectiveDelay > 0) {
            try {
                log.debug("⏳ 等待 {}ms 后重试...", effectiveDelay);
                Thread.sleep(effectiveDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        // 8. 执行重试
        String cause = throwable != null
                ? throwable.getClass().getSimpleName() + ": "
                    + truncate(throwable.getMessage(), 120)
                : "unknown";

        log.warn("🔄 [{}] 第 {}/{} 次重试 | 原因: {}",
                result.getName(), retryCount, effectiveMax, cause);

        return true;
    }

    // ======================== 方法级配置解析 ========================

    private void resolveMethodConfig(ITestResult result) {
        if (methodConfigResolved) return;
        methodConfigResolved = true;

        try {
            Method method = result.getMethod()
                    .getConstructorOrMethod()
                    .getMethod();
            Class<?> clazz = result.getMethod().getRealClass();

            Retryable retryable = ConditionalRetryTransformer.getRetryable(method, clazz);
            if (retryable != null) {
                int max = retryable.maxRetry();
                if (max >= 0) methodMaxRetry = max;

                long delay = retryable.delayMs();
                if (delay >= 0) methodDelayMs = delay;

                RetryOnAssertion roa = retryable.retryOnAssertion();
                if (roa == RetryOnAssertion.YES) methodRetryOnAssertion = true;
                else if (roa == RetryOnAssertion.NO) methodRetryOnAssertion = false;
            }
        } catch (Exception e) {
            log.warn("读取 @Retryable 失败，使用全局配置: {}", e.getMessage());
        }
    }

    // ======================== 配置优先级 ========================

    private int resolveMaxRetry() {
        if (methodMaxRetry != null && methodMaxRetry >= 0) return methodMaxRetry;
        return RetryConfig.getMaxRetry();
    }

    private long resolveDelayMs() {
        if (methodDelayMs != null && methodDelayMs >= 0) return methodDelayMs;
        return RetryConfig.getRetryDelayMs();
    }

    private boolean resolveRetryOnAssertion() {
        if (methodRetryOnAssertion != null) return methodRetryOnAssertion;
        return RetryConfig.isRetryOnAssertion();
    }

    // ======================== 异常分析 ========================

    private boolean isRetryableException(Throwable throwable) {
        Set<String> skipSet = RetryConfig.getSkipExceptions();
        if (skipSet.isEmpty()) return true;

        Throwable current = throwable;
        while (current != null) {
            String name = current.getClass().getName();
            String simple = current.getClass().getSimpleName();
            if (skipSet.contains(name) || skipSet.contains(simple)) {
                return false;
            }
            current = current.getCause();
        }
        return true;
    }

    // ======================== 辅助 ========================

    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    public int getRetryCount() {
        return retryCount;
    }
}
