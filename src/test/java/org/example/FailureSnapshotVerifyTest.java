package org.example;

import org.example.base.listener.FailureListener;
import org.example.base.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

/**
 * 失败截图链路验证用例 — 不依赖后端.
 *
 * <p>跳过真实登录，直接用 data URL 造一个存活的 Page，然后强制失败，
 * 用于验证：截图 → StepContext.snapshot → result attribute → FailureListener
 * → FailureHandler 落盘 的完整链路。</p>
 *
 * <p>验证完成后可删除本类。</p>
 */
@Listeners(FailureListener.class)
public class FailureSnapshotVerifyTest extends BaseTest {

    @Override
    protected boolean shouldPerformLogin() {
        return false; // 不连后端
    }

    @Test
    public void shouldCaptureScreenshotOnFailure() {
        getPage().navigate("data:text/html,<h1>Failure Capture Verify</h1>");
        Assert.fail("故意失败，用于验证截图链路");
    }
}
