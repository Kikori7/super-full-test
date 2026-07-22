package org.example.base.util;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.ScreenshotType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 截图原子服务 — 只负责"把当前页面变成字节"，不感知 TestNG / 报告 / 落盘.
 *
 * <h3>定位</h3>
 * 纯工具方法，无状态。上层（BaseTest 的 @AfterMethod）在 Page 还活着时调用，
 * 拿到 byte[] 后填进 {@link org.example.base.context.FailureSnapshot}。
 * 具体怎么用（落盘 / 附加到报告）由 {@link FailureHandler} 决定，本类一概不管。
 *
 * <h3>容错约定</h3>
 * 任何异常（Page 为 null、已关闭、渲染超时）都不向上抛，返回 {@code null}。
 * 截图是"锦上添花"的诊断信息，绝不能因为截图失败而影响主流程或掩盖真正的测试失败。
 *
 * @author Kiko Song
 * @since 2026-07-09
 */
public final class ScreenshotService {

    private static final Logger log = LoggerFactory.getLogger(ScreenshotService.class);

    /**
     * 整页截图（含滚动区域），返回 PNG 字节.
     *
     * @param page Playwright 页面，可为 null
     * @return PNG 字节；page 不可用或截图失败时返回 null
     */
    public static byte[] captureFullPage(Page page) {
        if (page == null) {
            log.debug("跳过截图：Page 为 null");
            return null;
        }
        try {
            return page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(true)
                    .setType(ScreenshotType.PNG));
        } catch (Exception e) {
            log.warn("整页截图失败（不影响主流程）: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 可视区域截图（仅当前视口），返回 PNG 字节.
     * 页面极长导致整页截图过慢/超时的场景可用此方法兜底。
     */
    public static byte[] captureViewport(Page page) {
        if (page == null) {
            log.debug("跳过截图：Page 为 null");
            return null;
        }
        try {
            return page.screenshot(new Page.ScreenshotOptions()
                    .setFullPage(false)
                    .setType(ScreenshotType.PNG));
        } catch (Exception e) {
            log.warn("视口截图失败（不影响主流程）: {}", e.getMessage());
            return null;
        }
    }

    private ScreenshotService() {}
}
