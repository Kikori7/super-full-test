package org.example;

import org.example.base.context.StepContext;
import org.example.base.test.BaseTest;
import org.testng.annotations.Test;

/**
 * StepContext 使用演示 —— 不依赖真实页面，展示完整生命周期.
 *
 * <h3>运行方式</h3>
 * <pre>{@code
 * mvn test -Dtest=StepContextDemoTest
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-27
 */
public class StepContextDemoTest extends BaseTest {

    /**
     * 演示 1：StepContext 是怎么自动累积步骤的.
     *
     * 真实场景对应：
     * <pre>{@code
     * ProductPage page = new ProductPage(getPage());
     * page.navigate("http://localhost:8080");
     * page.clickMerchantTab("商家A");
     * page.addToCart("iPhone 15");
     * }</pre>
     */
    @Test(description = "演示：StepContext 自动记录操作步骤")
    public void demo1_StepAutoRecording() {
        System.out.println("\n===== demo1: ThreadLocal 自动记录步骤 =====\n");

        // —— 这些调用在真实代码里分布在不同的 Page 对象中，
        //    但每个操作最终都走到 BasePage 的方法，BasePage 内部调 StepContext.record()

        // 模拟：navigate("http://localhost:8080")
        //       → BasePage.navigate() 内部调 StepContext.recordEntry(url)
        StepContext.recordEntry("http://localhost:8080");

        // 模拟：clickMerchantTab("商家A")
        //       → 内部调 BasePage.click(locator) → StepContext.record(...)
        StepContext.record("🖱️ 点击 [商家A]");

        // 模拟：addToCart("iPhone 15")
        //       → 内部调 BasePage.click(button) → StepContext.record(...)
        StepContext.record("🖱️ 点击 [加入购物车]");

        // 模拟：fill 搜索框
        StepContext.record("⌨️ 输入 [\"手机\"] → input#search");

        // —— 此时 ThreadLocal 里已经累积了 4 步 ——

        System.out.println("当前线程: " + Thread.currentThread().getName());
        System.out.println("上一步:   " + StepContext.getLastStep());
        System.out.println("完整链:   " + StepContext.getStepChain());
        System.out.println("测试名:   " + StepContext.getTestName());

        /* 输出类似：
           当前线程: pool-1-thread-1
           上一步:   ⌨️ 输入 ["手机"] → input#search
           完整链:   [🌐 进入页面: http://localhost:8080, 🖱️ 点击 [商家A], 🖱️ 点击 [加入购物车], ⌨️ 输入 ["手机"] → input#search]
           测试名:   StepContextDemoTest.demo1_StepAutoRecording
        */
    }

    /**
     * 演示 2：异常发生时如何冻结现场.
     *
     * 真实场景对应：
     * {@code GlobalExceptionHandler} (还没写) 在未来 catch 到异常时调用 snapshot().
     */
    @Test(description = "演示：异常时冻结 StepContext 到 FailureSnapshot")
    public void demo2_SnapshotOnFailure() {
        System.out.println("\n===== demo2: 异常时冻结现场 =====\n");

        // 正常执行，累积步骤
        StepContext.record("🖱️ 点击 [iPhone 15] 加入购物车");
        StepContext.put("merchantId", "5");
        StepContext.put("productName", "iPhone 15");

        // 假设这里 💥 异常了！
        String pageUrl = "http://localhost:8080/products?merchantId=5";
        String pageTitle = "购物车";

        // 冻结现场（你未来在全局异常处理里做的事）
        var snapshot = StepContext.snapshot(pageUrl, pageTitle);

        // 打印快照 — 排查问题一目了然
        System.out.println(snapshot);

        /* 输出类似：
           ┌─────────── Failure Snapshot ───────────
           │ 时间: 2026-06-27 20:30:15.123
           │ 用例: StepContextDemoTest.demo2_SnapshotOnFailure
           │ 当前URL: http://localhost:8080/products?merchantId=5
           │ 进入URL: null
           │ 页面标题: 购物车
           │ 上一步: 🖱️ 点击 [iPhone 15] 加入购物车
           │ 步骤链:
           │   1. 🖱️ 点击 [iPhone 15] 加入购物车
           │ 扩展数据: {merchantId=5, productName=iPhone 15}
           └──────────────────────────────────────────
        */
    }

    /**
     * 演示 3：ThreadLocal 线程隔离 —— 两个并发用例互不干扰.
     *
     * 真实场景：TestNG parallel="methods" thread-count="2"
     * 线程 A 执行 testAddToCart，线程 B 同时执行 testRemoveItem
     * 各自的 StepContext 完全独立。
     */
    @Test(description = "演示：ThreadLocal 隔离验证")
    public void demo3_ThreadIsolation() {
        System.out.println("\n===== demo3: ThreadLocal 隔离 =====\n");

        String threadName = Thread.currentThread().getName();

        // 当前线程里写入自己的步骤
        StepContext.record("📦 线程 " + threadName + " 的步骤 1");
        StepContext.record("📦 线程 " + threadName + " 的步骤 2");

        System.out.println("线程: " + threadName);
        System.out.println("步骤链: " + StepContext.getStepChain());

        // 如果并发跑，另一个线程的 StepContext.getStepChain() 不会包含这里的步骤
    }

    /**
     * 演示 4：StepContext 生命周期 — BeforeMethod 清空，AfterMethod 清空.
     * 你可以看控制台输出：每个 @Test 开始时 StepContext 都是空的。
     */
    @Test(description = "演示：每个用例开始时 StepContext 都是干净的")
    public void demo4_FreshStartEachTest() {
        System.out.println("\n===== demo4: 每个用例从 0 开始 =====\n");

        // BaseTest @BeforeMethod 已经调了 StepContext.init()
        // 此时 StepContext 是干净的

        System.out.println("测试名:  " + StepContext.getTestName());
        System.out.println("初始栈:  " + StepContext.getStepChain());  // → []
        System.out.println("上一步:  " + StepContext.getLastStep());    // → null

        StepContext.record("🖱️ 点击 [提交]");

        System.out.println("操作后:  " + StepContext.getStepChain());  // → [🖱️ 点击 [提交]]
    }
}
