package org.example.base.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.MouseButton;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.example.base.context.StepContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 页面对象基类 — 封装 Playwright Page 通用操作.
 *
 * <h3>设计思路</h3>
 * <ul>
 *   <li>Playwright 的 Locator 自带自动等待，所以不需要在每个操作前加显式等待</li>
 *   <li>只对"自动等待覆盖不到"的场景封装显式等待：页面加载、URL 变化、元素消失</li>
 *   <li>操作失败时自动截图，方便排查</li>
 *   <li>页面跳转返回新页面对象，支持链式调用</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * public class HomePage extends BasePage {
 *     public HomePage(Page page) { super(page); }
 *
 *     public ProductPage search(String keyword) {
 *         fill("#search-input", keyword);
 *         click(findByRole(AriaRole.BUTTON, "搜索"));
 *         return navigateTo(ProductPage.class);
 *     }
 * }
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
public abstract class BasePage {

    protected final Page page;
    protected final Logger log = LoggerFactory.getLogger(getClass());

    // ======================== 超时配置 ========================

    /** 默认显式等待超时（毫秒） */
    protected long explicitTimeout = 10_000;

    /** 默认页面加载超时（毫秒） */
    protected long pageLoadTimeout = 30_000;

    // ======================== 构造器 ========================

    public BasePage(Page page) {
        this.page = page;
        this.page.setDefaultTimeout(explicitTimeout);
    }

    // ======================== 导航 ========================

    /** 跳转到指定 URL，等待页面 DOM 加载完成 */
    public void navigate(String url) {
        log.info("🌐 导航到: {}", url);
        page.navigate(url);
        waitForPageLoad();
        StepContext.recordEntry(url);
    }

    /** 当前页面 URL */
    public String getCurrentUrl() {
        return page.url();
    }

    /** 页面标题 */
    public String getTitle() {
        return page.title();
    }

    /** 刷新页面 */
    public void refresh() {
        page.reload();
        waitForPageLoad();
        StepContext.record("🔄 刷新页面");
    }

    /** 浏览器后退 */
    public void goBack() {
        page.goBack();
        waitForPageLoad();
        StepContext.record("⬅ 浏览器后退 → " + page.url());
    }

    // ======================== 元素定位（快捷方法） ========================

    /**
     * CSS / XPath 选择器定位.
     * Playwright 的 Locator 是懒加载 + 自动等待的，不需要预先 waitFor。
     */
    public Locator find(String selector) {
        return page.locator(selector);
    }

    /** 文本内容定位 */
    public Locator findByText(String text) {
        return page.getByText(text);
    }

    /** 语义角色定位（推荐，最贴近用户行为） */
    public Locator findByRole(AriaRole role, String name) {
        return page.getByRole(role, new Page.GetByRoleOptions().setName(name));
    }

    /** placeholder 定位 */
    public Locator findByPlaceholder(String placeholder) {
        return page.getByPlaceholder(placeholder);
    }

    /** label 定位 */
    public Locator findByLabel(String label) {
        return page.getByLabel(label);
    }

    /** test-id 定位（推荐用于稳定的选择器） */
    public Locator findByTestId(String testId) {
        return page.getByTestId(testId);
    }

    /** 第 N 个匹配元素 */
    public Locator nth(Locator locator, int index) {
        return locator.nth(index);
    }

    /** 过滤：包含指定文本的 */
    public Locator filterByText(Locator locator, String text) {
        return locator.filter(new Locator.FilterOptions().setHasText(text));
    }

    // ======================== 显式等待 ========================

    /**
     * 等待页面加载到 network idle.
     * 常用于 navigate() 之后、或点击触发页面跳转后。
     */
    public void waitForPageLoad() {
        page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions()
                .setTimeout(pageLoadTimeout));
    }

    /** 等待 URL 匹配指定模式 */
    public void waitForUrl(String urlPattern) {
        page.waitForURL(urlPattern, new Page.WaitForURLOptions()
                .setTimeout(explicitTimeout));
    }

    /** 等待 URL 包含指定字符串 */
    public void waitForUrlContains(String substring) {
        page.waitForURL("**" + substring + "**", new Page.WaitForURLOptions()
                .setTimeout(explicitTimeout));
    }

    /** 等待元素在 DOM 中可见（通常不需要，Playwright 操作时自动等） */
    public void waitForVisible(String selector) {
        page.waitForSelector(selector, new Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.VISIBLE)
                .setTimeout(explicitTimeout));
    }

    /** 等待元素从 DOM 中消失 */
    public void waitForHidden(Locator locator) {
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.HIDDEN)
                .setTimeout(explicitTimeout));
    }

    /** 等待元素从 DOM 中分离 */
    public void waitForDetached(Locator locator) {
        locator.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.DETACHED)
                .setTimeout(explicitTimeout));
    }

    /** 等待指定毫秒（尽量避免使用，用于极端场景） */
    public void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ======================== 基础操作 ========================

    /** 点击元素（Playwright 自动等待可点击） */
    public void click(Locator locator) {
        log.debug("🖱️ 点击: {}", locator);
        locator.click();
        StepContext.record("🖱️ 点击 [" + describe(locator) + "]");
    }

    /** 双击 */
    public void doubleClick(Locator locator) {
        locator.dblclick();
        StepContext.record("🖱️ 双击 [" + describe(locator) + "]");
    }

    /** 右键 */
    public void rightClick(Locator locator) {
        locator.click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
        StepContext.record("🖱️ 右键 [" + describe(locator) + "]");
    }

    /** 输入文本（先清空再输入） */
    public void fill(Locator locator, String text) {
        log.debug("⌨️ 输入: {} → \"{}\"", locator, text);
        locator.fill(text);
        StepContext.record("⌨️ 输入 [\"" + text + "\"] → " + describe(locator));
    }

    /** 逐字输入（模拟真实打字，触发键盘事件） */
    public void type(Locator locator, String text) {
        locator.pressSequentially(text);
        StepContext.record("⌨️ 逐字输入 [\"" + text + "\"] → " + describe(locator));
    }

    /** 清空输入框 */
    public void clear(Locator locator) {
        locator.clear();
        StepContext.record("🧹 清空输入框 [" + describe(locator) + "]");
    }

    /** 下拉选择 */
    public void selectByValue(Locator locator, String value) {
        locator.selectOption(value);
        StepContext.record("📋 下拉选择 [\"" + value + "\"] → " + describe(locator));
    }

    /** 勾选 checkbox */
    public void check(Locator locator) {
        locator.check();
        StepContext.record("☑ 勾选 [" + describe(locator) + "]");
    }

    /** 取消勾选 */
    public void uncheck(Locator locator) {
        locator.uncheck();
        StepContext.record("☐ 取消勾选 [" + describe(locator) + "]");
    }

    /** 按键盘按键 */
    public void press(String key) {
        page.keyboard().press(key);
        StepContext.record("⌨️ 按键 [" + key + "]");
    }

    /** 上传文件 */
    public void uploadFile(Locator locator, Path... files) {
        locator.setInputFiles(files);
        StepContext.record("📎 上传文件 → " + describe(locator));
    }

    /** 上传文件（字符串路径） */
    public void uploadFile(Locator locator, String... filePaths) {
        Path[] paths = new Path[filePaths.length];
        for (int i = 0; i < filePaths.length; i++) {
            paths[i] = Paths.get(filePaths[i]);
        }
        locator.setInputFiles(paths);
        StepContext.record("📎 上传文件 → " + describe(locator));
    }

    // ======================== 状态检查 ========================

    /** 元素是否可见 */
    public boolean isVisible(Locator locator) {
        return locator.isVisible();
    }

    /** 元素是否可编辑 */
    public boolean isEnabled(Locator locator) {
        return locator.isEnabled();
    }

    /** 元素是否选中（checkbox/radio） */
    public boolean isChecked(Locator locator) {
        return locator.isChecked();
    }

    /** 获取元素数量 */
    public int count(String selector) {
        return page.locator(selector).count();
    }

    /** 获取元素文本 */
    public String getText(Locator locator) {
        return locator.textContent();
    }

    /** 获取元素内部文本（不含子元素文本） */
    public String getInnerText(Locator locator) {
        return locator.innerText();
    }

    /** 获取元素属性值 */
    public String getAttribute(Locator locator, String attributeName) {
        return locator.getAttribute(attributeName);
    }

    /** 获取输入框的值 */
    public String getValue(Locator locator) {
        return locator.inputValue();
    }

    // ======================== 高级交互 ========================

    /** 悬停 */
    public void hover(Locator locator) {
        locator.hover();
        StepContext.record("👆 悬停 [" + describe(locator) + "]");
    }

    /** 拖拽 */
    public void dragTo(Locator source, Locator target) {
        source.dragTo(target);
        StepContext.record("↔ 拖拽 [" + describe(source) + "] → [" + describe(target) + "]");
    }

    /** 滚动到元素可见 */
    public void scrollTo(Locator locator) {
        locator.scrollIntoViewIfNeeded();
        StepContext.record("📜 滚动至 [" + describe(locator) + "]");
    }

    /** 聚焦元素 */
    public void focus(Locator locator) {
        locator.focus();
        StepContext.record("🔍 聚焦 [" + describe(locator) + "]");
    }

    // ======================== 弹窗 / Toast / Dialog ========================

    /**
     * 处理浏览器原生弹窗（alert/confirm/prompt）.
     * 在触发弹窗的操作之前调用。
     */
    public void acceptDialog() {
        page.onDialog(dialog -> {
            log.debug("🔔 弹窗消息: {}，自动接受", dialog.message());
            dialog.accept();
        });
    }

    /** 取消弹窗 */
    public void dismissDialog() {
        page.onDialog(dialog -> {
            log.debug("🔔 弹窗消息: {}，自动取消", dialog.message());
            dialog.dismiss();
        });
    }

    /** 获取 Toast 文本（假设 toast 选择器为常见约定） */
    public String getToastText(String toastSelector) {
        Locator toast = page.locator(toastSelector);
        waitForVisible(toastSelector);
        String text = toast.innerText();
        log.debug("📢 Toast: {}", text);
        return text;
    }

    // ======================== iframe / 新窗口 ========================

    /** 在 iframe 内定位元素，返回可直接操作的 Locator */
    public Locator inFrame(String frameSelector, String innerSelector) {
        return page.frameLocator(frameSelector).locator(innerSelector);
    }

    /**
     * 等待新窗口/新标签页打开，返回新 Page.
     * 在触发新窗口的操作之前调用。
     */
    public Page waitForNewPage(Runnable trigger) {
        Page newPage = page.context().waitForPage(trigger);
        newPage.waitForLoadState(LoadState.NETWORKIDLE);
        return newPage;
    }

    // ======================== 截图 ========================

    /** 全屏截图，返回字节数组（用于附加到报告） */
    public byte[] screenshot() {
        return page.screenshot();
    }

    /** 截图保存到文件 */
    public void screenshot(String filePath) {
        page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(filePath)));
    }

    /** 元素截图 */
    public byte[] elementScreenshot(Locator locator) {
        return locator.screenshot();
    }

    // ======================== 页面跳转（工厂方法） ========================

    /**
     * 等待 URL 变化后，返回目标页面对象.
     *
     * <pre>{@code
     * ProductPage productPage = currentPage.navigateTo(ProductPage.class);
     * }</pre>
     *
     * @param pageClass 目标 Page 类型，必须有 Page 单参构造器
     */
    public <T extends BasePage> T navigateTo(Class<T> pageClass) {
        waitForPageLoad();
        StepContext.record("⏩ 进入页面: " + pageClass.getSimpleName());
        try {
            return pageClass.getDeclaredConstructor(Page.class).newInstance(this.page);
        } catch (Exception e) {
            throw new RuntimeException("无法创建页面对象: " + pageClass.getName()
                    + "，请确保有 public 的 Page 单参构造器", e);
        }
    }

    /**
     * 导航到指定 URL 并返回指定页面对象（一步到位）.
     */
    public <T extends BasePage> T navigateTo(String url, Class<T> pageClass) {
        navigate(url);
        return navigateTo(pageClass);
    }

    // ======================== 基础信息 ========================

    /**
     * 生成 Locator 的人类可读描述，用于步骤记录.
     * 优先提取 aria-label / text / placeholder，都没有就用 toString() 截断。
     */
    private String describe(Locator locator) {
        try {
            // 尝试获取有意义的信息
            String ariaLabel = locator.getAttribute("aria-label");
            if (ariaLabel != null && !ariaLabel.isBlank()) return ariaLabel;
            String placeholder = locator.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isBlank()) return placeholder;
            String text = locator.innerText();
            if (text != null && !text.isBlank()) return text.trim().replace("\n", " ");
        } catch (Exception ignored) {
            // locator 可能还没渲染，忽略
        }
        // fallback: toString() 截短
        String raw = locator.toString();
        return raw.length() > 60 ? raw.substring(0, 57) + "..." : raw;
    }

    /** 当前页面对象的简单描述（日志用） */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + page.url() + "]";
    }
}
