package org.example.base.util;

import org.example.base.context.FailureSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 失败处理原子服务 — 消费一个已冻结的 {@link FailureSnapshot}，产出可排查的产物.
 *
 * <h3>定位</h3>
 * 只知道"拿到一份失败现场快照后要做什么"，不知道快照怎么来的、截图怎么截的、
 * 更不知道 TestNG 的存在。与 {@link ScreenshotService} 一样是无状态工具。
 *
 * <h3>当前职责</h3>
 * <ol>
 *   <li>把快照里的截图字节落盘到 {@code target/screenshots/}</li>
 *   <li>把结构化的失败现场（步骤链、URL、异常）打到 error 日志</li>
 * </ol>
 *
 * <p>后续接入 Allure/Extent 时，在 {@link #handle} 里追加一行 attach 即可，
 * 调用方（监听器）无需改动。</p>
 *
 * @author Kiko Song
 * @since 2026-07-09
 */
public final class FailureHandler {

    private static final Logger log = LoggerFactory.getLogger(FailureHandler.class);

    /** 截图输出目录（相对项目根，随 target 一起被 clean） */
    private static final Path SCREENSHOT_DIR = Paths.get("target", "screenshots");

    /**
     * 处理一次失败：落盘截图 + 打印现场.
     *
     * @param snapshot 已冻结的失败快照，可为 null（此时仅记录告警）
     */
    public static void handle(FailureSnapshot snapshot) {
        if (snapshot == null) {
            log.warn("FailureHandler 收到 null 快照，跳过处理");
            return;
        }

        Path savedPath = persistScreenshot(snapshot);

        // 结构化现场：FailureSnapshot.toString() 已是多行框图，直接打
        log.error("测试失败现场:\n{}{}", snapshot,
                savedPath != null ? "\n│ 截图: " + savedPath.toAbsolutePath() : "");

        // TODO 接入 Allure 后在此 attach：
        //   if (snapshot.getScreenshot() != null)
        //       Allure.addAttachment(snapshot.getTestName(), "image/png",
        //               new ByteArrayInputStream(snapshot.getScreenshot()), ".png");
    }

    /**
     * 把快照里的截图字节写到 {@code target/screenshots/{testName}_{timestamp}.png}.
     *
     * @return 写入成功返回文件路径，无截图或写入失败返回 null
     */
    private static Path persistScreenshot(FailureSnapshot snapshot) {
        byte[] bytes = snapshot.getScreenshot();
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            String fileName = sanitize(snapshot.getTestName())
                    + "_" + snapshot.getTimestamp() + ".png";
            Path target = SCREENSHOT_DIR.resolve(fileName);
            Files.write(target, bytes);
            return target;
        } catch (IOException e) {
            log.warn("截图落盘失败（不影响主流程）: {}", e.getMessage());
            return null;
        }
    }

    /** 把用例名里的非法文件名字符替换掉，避免路径注入/非法字符 */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private FailureHandler() {}
}
