package org.example.base.retry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记测试方法/类不需要重试.
 *
 * <pre>{@code
 * @NoRetry
 * @Test
 * public void fastSmokeTest() { ... }
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoRetry {

    /** 跳过原因说明（可选，仅用于文档） */
    String value() default "";
}
