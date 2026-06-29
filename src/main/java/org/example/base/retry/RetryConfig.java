package org.example.base.retry;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 重试配置中心 — 支持外部参数动态注入.
 *
 * <h3>参数来源优先级</h3>
 * <ol>
 *   <li>JVM 系统属性 ({@code -Dkey=value}) — 命令行意图最明确，最高优先级</li>
 *   <li>testng.xml {@code <parameter>} 标签（通过 {@link #loadFromSuiteParams} 注入）</li>
 *   <li>代码内默认值</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // Maven 命令行
 * mvn test -Dtest.retry.enabled=true -Dtest.retry.max=3 -Dtest.retry.delay.ms=500
 *
 * // 关闭重试
 * mvn test -Dtest.retry.enabled=false
 *
 * // testng.xml
 * <parameter name="retryEnabled" value="true"/>
 * <parameter name="retryMax" value="3"/>
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
public final class RetryConfig {

    // ======================== 参数 key 常量 ========================

    /**
     * 系统属性：是否开启重试
     */
    public static final String KEY_ENABLED = "test.retry.enabled";
    /**
     * 系统属性：最大重试次数
     */
    public static final String KEY_MAX_RETRY = "test.retry.max";
    /**
     * 系统属性：每次重试前等待时间（毫秒）
     */
    public static final String KEY_DELAY_MS = "test.retry.delay.ms";
    /**
     * 系统属性：断言失败是否也重试
     */
    public static final String KEY_RETRY_ON_ASSERTION = "test.retry.on.assertion";
    /**
     * 系统属性：不重试的异常类名（逗号分隔）
     */
    public static final String KEY_SKIP_EXCEPTIONS = "test.retry.skip.exceptions";

    /**
     * testng.xml 参数名
     */
    public static final String SUITE_PARAM_ENABLED = "retryEnabled";
    public static final String SUITE_PARAM_MAX_RETRY = "retryMax";
    public static final String SUITE_PARAM_DELAY_MS = "retryDelayMs";
    public static final String SUITE_PARAM_RETRY_ON_ASSERTION = "retryOnAssertion";

    // ======================== 默认值 ========================

    private static final boolean DEFAULT_ENABLED = true;
    private static final int DEFAULT_MAX_RETRY = 2;
    private static final long DEFAULT_DELAY_MS = 0;
    private static final boolean DEFAULT_RETRY_ON_ASSERTION = true;

    // ======================== 缓存值（volatile + 懒加载） ========================

    private static volatile Boolean enabled;
    private static volatile Integer maxRetry;
    private static volatile Long retryDelayMs;
    private static volatile Boolean retryOnAssertion;
    private static volatile Set<String> skipExceptions;

    // ======================== 公开访问方法 ========================

    /**
     * 全局重试开关
     */
    public static boolean isRetryEnabled() {
        if (enabled == null) {
            synchronized (RetryConfig.class) {
                if (enabled == null) {
                    enabled = Boolean.parseBoolean(
                            System.getProperty(KEY_ENABLED,
                                    String.valueOf(DEFAULT_ENABLED)));
                }
            }
        }
        return enabled;
    }

    /**
     * 最大重试次数（0 表示不重试）
     */
    public static int getMaxRetry() {
        if (maxRetry == null) {
            synchronized (RetryConfig.class) {
                if (maxRetry == null) {
                    maxRetry = Integer.getInteger(KEY_MAX_RETRY, DEFAULT_MAX_RETRY);
                }
            }
        }
        return maxRetry;
    }

    /**
     * 两次重试之间的等待时间（毫秒）
     */
    public static long getRetryDelayMs() {
        if (retryDelayMs == null) {
            synchronized (RetryConfig.class) {
                if (retryDelayMs == null) {
                    retryDelayMs = Long.getLong(KEY_DELAY_MS, DEFAULT_DELAY_MS);
                }
            }
        }
        return retryDelayMs;
    }

    /**
     * 断言失败是否也触发重试（UI 测试建议开启）
     */
    public static boolean isRetryOnAssertion() {
        if (retryOnAssertion == null) {
            synchronized (RetryConfig.class) {
                if (retryOnAssertion == null) {
                    retryOnAssertion = Boolean.parseBoolean(
                            System.getProperty(KEY_RETRY_ON_ASSERTION,
                                    String.valueOf(DEFAULT_RETRY_ON_ASSERTION)));
                }
            }
        }
        return retryOnAssertion;
    }

    /**
     * 获取不应重试的异常类名集合
     */
    public static Set<String> getSkipExceptions() {
        if (skipExceptions == null) {
            synchronized (RetryConfig.class) {
                if (skipExceptions == null) {
                    String raw = System.getProperty(KEY_SKIP_EXCEPTIONS, "");
                    skipExceptions = new HashSet<>();
                    if (!raw.isBlank()) {
                        Arrays.stream(raw.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .forEach(skipExceptions::add);
                    }
                }
            }
        }
        return skipExceptions;
    }

    // ======================== testng.xml 参数注入入口 ========================

    /**
     * 由 {@link RetryConfigListener} 在 Suite 启动时调用，
     * 将 testng.xml 中的 {@code <parameter>} 值写入配置。
     * <p>
     * <b>优先级：命令行 -D 系统属性 > testng.xml 参数 > 代码默认值</b>
     * 仅当对应的系统属性未设置时，才应用 testng.xml 的值。
     */
    public static void loadFromSuiteParams(String retryEnabled,
                                           String retryMax,
                                           String retryDelayMs,
                                           String retryOnAssertion) {
        // 命令行 -D 优先级最高：系统属性已设 → 跳过 xml 参数
        if (retryEnabled != null && !retryEnabled.isBlank()
                && System.getProperty(KEY_ENABLED) == null) {
            enabled = Boolean.parseBoolean(retryEnabled.trim());
        }
        if (retryMax != null && !retryMax.isBlank()
                && System.getProperty(KEY_MAX_RETRY) == null) {
            try {
                maxRetry = Integer.parseInt(retryMax.trim());
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        if (retryDelayMs != null && !retryDelayMs.isBlank()
                && System.getProperty(KEY_DELAY_MS) == null) {
            try {
                RetryConfig.retryDelayMs = Long.parseLong(retryDelayMs.trim());
            } catch (NumberFormatException ignored) {
                // keep default
            }
        }
        if (retryOnAssertion != null && !retryOnAssertion.isBlank()
                && System.getProperty(KEY_RETRY_ON_ASSERTION) == null) {
            RetryConfig.retryOnAssertion = Boolean.parseBoolean(retryOnAssertion.trim());
        }
    }

    // ======================== 程序化控制（测试内部使用） ========================

    /**
     * 强制开启/关闭（优先级高于系统属性，用于特定场景）
     */
    public static void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 运行时重置所有配置为未加载状态（下次访问时重新读取系统属性）
     */
    public static void refresh() {
        enabled = null;
        maxRetry = null;
        retryDelayMs = null;
        retryOnAssertion = null;
        skipExceptions = null;
    }

    // ======================== 诊断 ========================

    /**
     * 打印当前配置（DEBUG 用）
     */
    public static String dump() {
        return String.format(
                "RetryConfig{ enabled=%s, maxRetry=%d, delayMs=%d, retryOnAssertion=%s, skipExceptions=%s }",
                isRetryEnabled(), getMaxRetry(), getRetryDelayMs(),
                isRetryOnAssertion(), getSkipExceptions());
    }

    private RetryConfig() {
        // 工具类，禁止实例化
    }
}
