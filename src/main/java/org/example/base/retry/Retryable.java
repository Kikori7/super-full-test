package org.example.base.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 测试方法级别的重试配置覆盖（优先级高于全局配置）.
 *
 * <pre>{@code
 * // 这个用例最多重试 5 次，每次间隔 1 秒
 * @Retryable(maxRetry = 5, delayMs = 1000)
 * @Test
 * public void flakyUITest() { ... }
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Retryable {

    /** 最大重试次数（-1 表示使用全局配置） */
    int maxRetry() default -1;

    /** 重试间隔（毫秒，-1 表示使用全局配置） */
    long delayMs() default -1;

    /** 断言失败是否重试（仅当值为 true/false 时覆盖全局，默认不覆盖） */
    RetryOnAssertion retryOnAssertion() default RetryOnAssertion.USE_GLOBAL;

    /**
     * 断言重试策略
     */
    enum RetryOnAssertion {
        /** 使用全局默认配置 */
        USE_GLOBAL,
        /** 强制重试 */
        YES,
        /** 强制跳过 */
        NO
    }
}
