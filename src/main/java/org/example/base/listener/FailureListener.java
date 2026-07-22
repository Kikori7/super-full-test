package org.example.base.listener;

import com.microsoft.playwright.Page;
import org.example.base.context.FailureSnapshot;
import org.example.base.context.StepContext;
import org.example.base.test.BaseTest;
import org.example.base.util.FailureHandler;
import org.example.base.util.ScreenshotService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

/**
 * 失败事件路由器 — 只做编排，不碰业务.
 *
 * <h3>触发时机</h3>
 * TestNG 的 {@code ITestListener.onTestFailure} 在 {@code @AfterMethod} 之前触发，
 * 此时 Page 仍存活，可直接截图。截图后交由 {@link FailureHandler} 落盘+报告。
 *
 * <h3>挂载方式</h3>
 * 推荐 testng.xml &lt;listener&gt; 或 ServiceLoader。
 *
 * @author Kiko Song
 * @since 2026-07-09
 */
public class FailureListener implements ITestListener {

    private static final Logger log = LoggerFactory.getLogger(FailureListener.class);

    @Override
    public void onTestFailure(ITestResult result) {
        try {
            Page page = BaseTest.currentPage();

            String url = null, title = null;
            if (page != null) {
                try { url = page.url(); } catch (Exception ignored) { }
                try { title = page.title(); } catch (Exception ignored) { }
            }

            byte[] screenshot = ScreenshotService.captureFullPage(page);
            FailureSnapshot snapshot = StepContext.snapshot(url, title);
            snapshot.setScreenshot(screenshot);

            Throwable t = result.getThrowable();
            if (t != null) {
                snapshot.setExceptionInfo(t.getClass().getName(), t.getMessage());
            }

            FailureHandler.handle(snapshot);
        } catch (Exception e) {
            log.warn("失败截图/现场处理异常（不影响测试结果）: {}", e.getMessage());
        }
    }
}
