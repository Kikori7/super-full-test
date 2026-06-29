package org.example.base.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;

/**
 * 智能重试分析器
 *
 * @author Kiko Song
 * @create 2026-06-09 14:46
 */
public class SmartRetryAnalyzer implements IRetryAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(SmartRetryAnalyzer.class);
    private int retryCount = 0;
//    private final int maxRetry = Integer.getInteger("test.retry.max", 2);
    private final int maxRetry = 3;

    @Override
    public boolean retry(ITestResult iTestResult) {
        if (retryCount >= maxRetry) {
            return false;
        }
        System.out.println("====== [DEBUG] retry() called for: " + iTestResult.getName()
                + ", status=" + iTestResult.getStatus() + ", count=" + retryCount);
        retryCount++;
        Throwable throwable = iTestResult.getThrowable();
        String errorMsg = throwable != null ? throwable.getMessage() : "unknown";

        log.warn("🔄 用例 [{}] 第 {}/{} 次重试 | 失败原因: {}",
                iTestResult.getName(), retryCount, maxRetry, errorMsg);

        return true;
    }
}
