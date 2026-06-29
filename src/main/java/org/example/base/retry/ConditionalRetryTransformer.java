package org.example.base.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 条件重试注入器 — 你原有 {@link GlobalRetryTransformer} 的增强版.
 *
 * <h3>增强点</h3>
 * <ul>
 *   <li>注入前检查 {@link RetryConfig#isRetryEnabled()} 全局开关</li>
 *   <li>识别并覆盖 TestNG 7.x 内置的 {@code DisabledRetryAnalyzer}</li>
 *   <li>支持 {@link NoRetry} / {@link Retryable} 注解细粒度控制</li>
 *   <li>支持分组排除（smoke / no-retry）</li>
 *   <li>注入的是 {@link DynamicRetryAnalyzer}，支持外部参数</li>
 * </ul>
 *
 * <h3>testng.xml 配置</h3>
 * <pre>{@code
 * <listeners>
 *     <listener class-name="base.RetryConfigListener"/>       <!-- 先读参数 -->
 *     <listener class-name="base.ConditionalRetryTransformer"/> <!-- 再注入 -->
 * </listeners>
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
public class ConditionalRetryTransformer implements IAnnotationTransformer {

    private static final Logger log = LoggerFactory.getLogger(ConditionalRetryTransformer.class);

    /** 默认不注入重试的分组 */
    private static final Set<String> EXCLUDE_GROUPS = new HashSet<>(Arrays.asList(
            "smoke", "no-retry"
    ));

    private static final String KEY_EXCLUDE_GROUPS = "test.retry.exclude.groups";

    // ======================== transform ========================

    @Override
    public void transform(ITestAnnotation annotation,
                          Class testClass,
                          Constructor testConstructor,
                          Method testMethod) {

        // ---- 1. 全局开关 ----
        if (!RetryConfig.isRetryEnabled()) {
            log.info("🌐 全局重试已关闭");
            return;
        }

        // ---- 2. @NoRetry ----
        if (hasNoRetry(testMethod, testClass)) {
            log.info("⏭️ @NoRetry → {}",
                    testMethod != null ? testMethod.getName() : testClass.getSimpleName());
            return;
        }

        // ---- 3. 分组排除 ----
        if (isInExcludedGroup(annotation)) {
            log.info("⏭️ 排除分组 [{}] → {}",
                    Arrays.toString(annotation.getGroups()),
                    testMethod != null ? testMethod.getName() : testClass.getSimpleName());
            return;
        }

        // ---- 4. 已有自定义 RetryAnalyzer（非 TestNG 默认）→ 保留 ----
        Class<? extends IRetryAnalyzer> existing = annotation.getRetryAnalyzerClass();
        if (existing != null && !isTestNGDefaultAnalyzer(existing)) {
            log.debug("🔧 保留已有 RetryAnalyzer [{}] → {}",
                    existing.getSimpleName(),
                    testMethod != null ? testMethod.getName() : testClass.getSimpleName());
            return;
        }

        // ---- 5. 注入 DynamicRetryAnalyzer ----
        annotation.setRetryAnalyzer(DynamicRetryAnalyzer.class);
        log.debug("✅ 注入 DynamicRetryAnalyzer → {}",
                testMethod != null ? testMethod.getName() : testClass.getSimpleName());
    }

    // ======================== 判断方法 ========================

    private boolean hasNoRetry(Method method, Class<?> clazz) {
        if (method != null && method.isAnnotationPresent(NoRetry.class)) {
            return true;
        }
        if (clazz != null && clazz.isAnnotationPresent(NoRetry.class)) {
            if (method == null || !method.isAnnotationPresent(Retryable.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean isInExcludedGroup(ITestAnnotation annotation) {
        String[] groups = annotation.getGroups();
        if (groups == null || groups.length == 0) return false;

        Set<String> excludeSet = new HashSet<>(EXCLUDE_GROUPS);
        String extra = System.getProperty(KEY_EXCLUDE_GROUPS, "");
        if (!extra.isBlank()) {
            Arrays.stream(extra.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(excludeSet::add);
        }
        return Arrays.stream(groups).anyMatch(excludeSet::contains);
    }

    /**
     * 判断是 TestNG 内置默认实现（非用户自定义），需要覆盖.
     * TestNG 7.x 会给未显式设置 retryAnalyzer 的方法注入 DisabledRetryAnalyzer.
     */
    private static boolean isTestNGDefaultAnalyzer(Class<? extends IRetryAnalyzer> clazz) {
        String name = clazz.getName();
        return name.equals("org.testng.IRetryAnalyzer")
                || name.contains("DisabledRetryAnalyzer")
                || name.contains("NoOp");
    }

    // ======================== 注解工具方法 ========================

    /**
     * 读取方法或类上的 {@link Retryable}（方法级优先）.
     * 也供 {@link DynamicRetryAnalyzer} 调用.
     */
    static Retryable getRetryable(Method method, Class<?> clazz) {
        if (method != null && method.isAnnotationPresent(Retryable.class)) {
            return method.getAnnotation(Retryable.class);
        }
        if (clazz != null && clazz.isAnnotationPresent(Retryable.class)) {
            return clazz.getAnnotation(Retryable.class);
        }
        return null;
    }
}
