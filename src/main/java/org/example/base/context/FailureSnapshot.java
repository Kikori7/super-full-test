package org.example.base.context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 失败上下文快照 — 异常发生瞬间冻结下来的现场信息.
 *
 * <p>不可变对象，由 {@link StepContext#snapshot(String, String)} 创建。
 * 创建后即可独立存储、传参，不依赖 ThreadLocal。
 *
 * <h3>包含信息</h3>
 * <ul>
 *   <li><b>时间</b>：异常发生的精确时刻</li>
 *   <li><b>身份</b>：哪个用例</li>
 *   <li><b>空间</b>：当前页面 URL + 标题 + 进入时的 URL</li>
 *   <li><b>轨迹</b>：步骤调用链（从进入页面到异常位置）</li>
 *   <li><b>业务</b>：扩展数据（商家、商品等）</li>
 * </ul>
 *
 * @author Kiko Song
 * @since 2026-06-27
 */
public final class FailureSnapshot {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    // ======================== 时间维度 ========================

    /** 快照创建时间戳 */
    private final long timestamp;
    /** 格式化的时间字符串 */
    private final String timeStr;

    // ======================== 身份维度 ========================

    /** 测试用例名 */
    private final String testName;

    // ======================== 空间维度 ========================

    /** 用例开始时进入的页面 URL */
    private final String entryUrl;
    /** 异常发生时所在的页面 URL */
    private final String currentUrl;
    /** 页面标题 */
    private final String pageTitle;

    // ======================== 执行轨迹维度 ========================

    /** 上一步操作描述 */
    private final String lastStep;
    /** 完整步骤调用链（按时间顺序，最早的在前面） */
    private final List<String> stepChain;

    // ======================== 业务维度 ========================

    /** 自定义扩展数据（不可变视图） */
    private final Map<String, Object> extras;

    // ======================== 截图维度（后续填充） ========================

    /** 截图字节（可在创建快照后异步填充） */
    private byte[] screenshot;
    /** 元素截图 */
    private byte[] elementScreenshot;

    // ======================== 异常维度（分类后填充） ========================

    private String exceptionClass;
    private String exceptionMessage;
    private String exceptionCategory;

    // ======================== 构造器 ========================

    FailureSnapshot(String testName, String lastStep, List<String> stepChain,
                    String entryUrl, String currentUrl, String pageTitle,
                    Map<String, Object> extras, long timestamp) {
        this.timestamp = timestamp;
        this.timeStr = TIME_FMT.format(Instant.ofEpochMilli(timestamp));
        this.testName = testName;
        this.lastStep = lastStep;
        this.stepChain = stepChain;
        this.entryUrl = entryUrl;
        this.currentUrl = currentUrl;
        this.pageTitle = pageTitle;
        this.extras = Collections.unmodifiableMap(extras);
    }

    // ======================== setter（构建后补充） ========================

    public void setScreenshot(byte[] screenshot) { this.screenshot = screenshot; }
    public void setElementScreenshot(byte[] bytes) { this.elementScreenshot = bytes; }
    public void setExceptionInfo(String clazz, String msg) {
        this.exceptionClass = clazz;
        this.exceptionMessage = msg;
    }
    public void setExceptionCategory(String category) { this.exceptionCategory = category; }

    // ======================== getter ========================

    public long getTimestamp() { return timestamp; }
    public String getTimeStr() { return timeStr; }
    public String getTestName() { return testName; }
    public String getEntryUrl() { return entryUrl; }
    public String getCurrentUrl() { return currentUrl; }
    public String getPageTitle() { return pageTitle; }
    public String getLastStep() { return lastStep; }
    public List<String> getStepChain() { return stepChain; }
    public Map<String, Object> getExtras() { return extras; }
    public Object getExtra(String key) { return extras.get(key); }
    public byte[] getScreenshot() { return screenshot; }
    public byte[] getElementScreenshot() { return elementScreenshot; }
    public String getExceptionClass() { return exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public String getExceptionCategory() { return exceptionCategory; }

    // ======================== 格式化输出 ========================

    /** 结构化摘要，方便在日志/报告中打印 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("┌─────────── Failure Snapshot ───────────\n");
        sb.append("│ 时间: ").append(timeStr).append("\n");
        sb.append("│ 用例: ").append(testName).append("\n");
        sb.append("│ 当前URL: ").append(currentUrl).append("\n");
        sb.append("│ 进入URL: ").append(entryUrl).append("\n");
        sb.append("│ 页面标题: ").append(pageTitle).append("\n");
        if (exceptionClass != null) {
            sb.append("│ 异常: ").append(exceptionClass).append(": ").append(exceptionMessage).append("\n");
        }
        if (exceptionCategory != null) {
            sb.append("│ 分类: ").append(exceptionCategory).append("\n");
        }
        sb.append("│ 上一步: ").append(lastStep).append("\n");
        sb.append("│ 步骤链:\n");
        for (int i = 0; i < stepChain.size(); i++) {
            sb.append("│   ").append(i + 1).append(". ").append(stepChain.get(i)).append("\n");
        }
        if (!extras.isEmpty()) {
            sb.append("│ 扩展数据: ").append(extras).append("\n");
        }
        sb.append("└──────────────────────────────────────────");
        return sb.toString();
    }
}
