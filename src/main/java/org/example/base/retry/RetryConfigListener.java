package org.example.base.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;

/**
 * 在 Suite 启动时从 testng.xml 读取 {@code <parameter>} 并注入 {@link RetryConfig}.
 *
 * <h3>testng.xml 配置示例</h3>
 * <pre>{@code
 * <suite name="MySuite">
 *   <parameter name="retryEnabled" value="true"/>
 *   <parameter name="retryMax" value="3"/>
 *   <parameter name="retryDelayMs" value="500"/>
 *
 *   <listeners>
 *     <listener class-name="base.RetryConfigListener"/>
 *     <listener class-name="base.GlobalRetryTransformer"/>
 *   </listeners>
 *   ...
 * </suite>
 * }</pre>
 *
 * <p><b>注意：</b>本 Listener 必须在 {@link GlobalRetryTransformer} 之前声明，
 * 以确保参数注入发生在 Transformer 执行之前。</p>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
public class RetryConfigListener implements ISuiteListener {

    private static final Logger log = LoggerFactory.getLogger(RetryConfigListener.class);

    @Override
    public void onStart(ISuite suite) {
        String suiteName = suite.getName();

        String enabled = suite.getParameter(RetryConfig.SUITE_PARAM_ENABLED);
        String maxRetry = suite.getParameter(RetryConfig.SUITE_PARAM_MAX_RETRY);
        String delayMs = suite.getParameter(RetryConfig.SUITE_PARAM_DELAY_MS);
        String retryOnAssertion = suite.getParameter(RetryConfig.SUITE_PARAM_RETRY_ON_ASSERTION);

        RetryConfig.loadFromSuiteParams(enabled, maxRetry, delayMs, retryOnAssertion);

        log.info("📋 Suite [{}] 重试配置: {}", suiteName, RetryConfig.dump());
    }

    @Override
    public void onFinish(ISuite suite) {
        // no-op
    }
}
