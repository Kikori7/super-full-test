package org.example;

import org.example.base.retry.NoRetry;
import org.example.base.retry.Retryable;
import org.example.base.retry.Retryable.RetryOnAssertion;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * DynamicRetryAnalyzer + ConditionalRetryTransformer 演示.
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 * # 默认配置（重试开启，最多2次）
 * mvn test
 *
 * # 自定义重试
 * mvn test -Dtest.retry.max=5 -Dtest.retry.delay.ms=500
 *
 * # 关闭重试
 * mvn test -Dtest.retry.enabled=false
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
public class RetryDemoTest {

    // ======================== 全局配置（无注解） ========================

    private int counter1 = 0;

    @Test(description = "无注解 → 使用全局配置重试")
    public void testGlobalRetryConfig() {
        counter1++;
        System.out.println("testGlobalRetryConfig 执行次数: " + counter1);
        Assert.assertEquals(counter1, 3, "前2次失败，第3次成功");
    }

    // ======================== @Retryable 自定义 ========================

    private int counter2 = 0;

    @Test(description = "@Retryable: 覆盖全局，maxRetry=5")
    @Retryable(maxRetry = 5)
    public void testCustomMaxRetry() {
        counter2++;
        System.out.println("testCustomMaxRetry 执行次数: " + counter2);
        Assert.assertEquals(counter2, 3, "前2次失败，第3次成功");
    }

    // ======================== @NoRetry 跳过 ========================

    private int counter3 = 0;

    @Test(description = "@NoRetry: 永不重试，一次通过")
    @NoRetry("冒烟测试")
    public void testNoRetry() {
        counter3++;
        System.out.println("testNoRetry 执行次数: " + counter3 + "（应只执行1次）");
        Assert.assertEquals(counter3, 1, "不重试，第一次即成功");
    }

    // ======================== 断言失败不重试 ========================

    private int counter4 = 0;

    @Test(description = "@Retryable(retryOnAssertion=NO): 断言失败不重试")
    @Retryable(retryOnAssertion = RetryOnAssertion.NO)
    public void testNoRetryOnAssertion() {
        counter4++;
        System.out.println("testNoRetryOnAssertion 执行次数: " + counter4 + "（应只执行1次）");
        Assert.assertEquals(counter4, 1, "断言不重试，第一次即成功");
    }

    // ======================== 正常用例 ========================

    @Test(description = "正常通过，不受影响")
    public void testNormalPass() {
        System.out.println("testNormalPass — 永远成功");
        Assert.assertTrue(true);
    }
}
