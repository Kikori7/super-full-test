package org.example.base.test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.example.base.context.StepContext;
import org.example.base.page.LoginPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 测试基类 — Playwright 生命周期 + 登录态复用 + 上下文管理.
 *
 * <h3>登录态复用机制</h3>
 * <ol>
 *   <li>{@code @BeforeSuite}：真正走一遍 UI 登录流程，拿到真实 token</li>
 *   <li>调用 {@code context.storageState()} 将 cookies + localStorage 导出到文件</li>
 *   <li>后续每个 {@code @Test} 创建 BrowserContext 时加载这个文件 → 免登录</li>
 * </ol>
 *
 * <h3>子类需要覆盖</h3>
 * <ul>
 *   <li>{@link #getTestUsername()} / {@link #getTestPassword()} — 测试账号</li>
 *   <li>{@link #getBaseUrl()} — 被测系统地址（默认 localhost:8080）</li>
 * </ul>
 *
 * @author Kiko Song
 * @since 2026-06-27
 */
public abstract class BaseTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    // ======================== 静态共享资源 ========================

    /** 整个 Suite 只启动一次浏览器 */
    private static Playwright playwright;
    private static Browser browser;

    /**
     * 登录后的 storage state 文件路径.
     * @BeforeSuite 写入，后续每个 @Test 读取。
     */
    private static final Path AUTH_STATE_PATH =
            Paths.get("target/auth-state.json");

    /** 是否已完成登录（Suite 级只登一次） */
    private static boolean loginCompleted;

    // ======================== 线程隔离资源 ========================

    /** 每个 @Test 独立的 BrowserContext */
    private final ThreadLocal<BrowserContext> threadContext = new ThreadLocal<>();
    private final ThreadLocal<Page> threadPage = new ThreadLocal<>();

    // ======================== 子类覆盖配置 ========================

    protected BrowserType.LaunchOptions getLaunchOptions() {
        return new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(java.util.Arrays.asList("--no-sandbox", "--disable-gpu"));
    }

    protected Browser.NewContextOptions getContextOptions() {
        return new Browser.NewContextOptions()
                .setViewportSize(1920, 1080)
                .setLocale("zh-CN");
    }

    protected String getBaseUrl() {
        return "http://localhost:8080";
    }

    /** 测试账号 — 用户名 */
    protected String getTestUsername() {
        return "testuser";
    }

    /** 测试账号 — 密码 */
    protected String getTestPassword() {
        return "123456";
    }

    /** 是否需要执行真实登录（默认 true，子类可覆盖跳过） */
    protected boolean shouldPerformLogin() {
        return true;
    }

    // ======================== Suite 级：登录一次，到处复用 ========================

    @BeforeSuite
    public void suiteLogin() {
        if (!shouldPerformLogin()) {
            log.info("⏭️ 跳过真实登录（shouldPerformLogin=false）");
            return;
        }

        launchBrowserIfNeeded();

        BrowserContext loginContext = null;
        try {
            log.info("===== Suite 级登录开始 =====");

            // 1. 创建临时 Context 用于登录
            loginContext = browser.newContext(getContextOptions());
            Page loginPage = loginContext.newPage();

            // 2. 打开应用 → 自动跳转登录页
            loginPage.navigate(getBaseUrl());
            loginPage.waitForLoadState(
                    com.microsoft.playwright.options.LoadState.NETWORKIDLE);

            // 3. 执行真实 UI 登录
            LoginPage login = new LoginPage(loginPage);
            login.login(getTestUsername(), getTestPassword());

            // 4. 导出登录态（cookies + localStorage + sessionStorage）
            loginContext.storageState(
                    new BrowserContext.StorageStateOptions()
                            .setPath(AUTH_STATE_PATH));
            log.info("💾 登录态已保存到: {}", AUTH_STATE_PATH.toAbsolutePath());

            loginCompleted = true;
            log.info("===== Suite 级登录完成 =====");

        } catch (Exception e) {
            log.error("❌ Suite 级登录失败: {}", e.getMessage());
            throw new RuntimeException("登录失败，后续用例无法执行", e);
        } finally {
            if (loginContext != null) {
                loginContext.close();
            }
        }
    }

    // ======================== Method 级生命周期 ========================

    @BeforeMethod
    public void baseSetUp(Method method) {
        launchBrowserIfNeeded();

        // 1. StepContext
        String testName = method.getDeclaringClass().getSimpleName()
                + "." + method.getName();
        StepContext.init(testName);
        log.info("━━━ {} ━━━ 开始", testName);

        // 2. 创建 BrowserContext（如果已有登录态则加载，否则裸创建）
        BrowserContext context;
        if (loginCompleted && java.nio.file.Files.exists(AUTH_STATE_PATH)) {
            // 复用登录态 — 这个 Context 自带 cookies + token
            context = browser.newContext(
                    getContextOptions().setStorageStatePath(AUTH_STATE_PATH));
            log.debug("🔐 加载已有登录态");
        } else {
            context = browser.newContext(getContextOptions());
            log.debug("📄 裸创建 Context（无登录态）");
        }
        Page page = context.newPage();

        threadContext.set(context);
        threadPage.set(page);
    }

    @AfterMethod
    public void baseTearDown(Method method, ITestResult result) {
        String testName = method.getDeclaringClass().getSimpleName()
                + "." + method.getName();

        try {
            BrowserContext context = threadContext.get();
            if (context != null) {
                context.close();
                threadContext.remove();
            }
            threadPage.remove();
        } catch (Exception e) {
            log.warn("关闭 BrowserContext 时异常: {}", e.getMessage());
        }

        StepContext.clear();
        log.info("━━━ {} ━━━ 结束 [{}]",
                testName, result.isSuccess() ? "PASS" : "FAIL");
    }

    @AfterSuite
    public void suiteCleanup() {
        shutdownBrowser();
        try {
            java.nio.file.Files.deleteIfExists(AUTH_STATE_PATH);
            log.debug("🧹 登录态文件已清理");
        } catch (Exception e) {
            // ignore
        }
    }

    // ======================== 辅助 ========================

    private void launchBrowserIfNeeded() {
        if (playwright == null) {
            synchronized (BaseTest.class) {
                if (playwright == null) {
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch(getLaunchOptions());
                    log.info("🚀 浏览器启动完成");
                }
            }
        }
    }

    public static void shutdownBrowser() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        if (playwright != null) {
            playwright.close();
            playwright = null;
        }
    }

    protected Page getPage() {
        return threadPage.get();
    }

    protected BrowserContext getContext() {
        return threadContext.get();
    }
}
