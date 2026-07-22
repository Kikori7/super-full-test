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
    /** 每个 @Test 独立的 Page — 静态以允许 FailureListener 在 @AfterMethod 之前获取 */
    private static final ThreadLocal<Page> threadPage = new ThreadLocal<>();

    /** 供 FailureListener 在 onTestFailure 时获取当前 Page（@AfterMethod 之前 Page 仍存活） */
    public static Page currentPage() {
        return threadPage.get();
    }

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

    /**
     * 当真实登录被跳过时（shouldPerformLogin=false 或登录失败），
     * 用此脚本注入假 token。子类覆盖即可。
     */
    protected String getAuthInitScript() {
        return null;
    }

    // ======================== Suite 级：登录一次，到处复用 ========================

    @BeforeSuite
    public void suiteLogin() {
        if (!shouldPerformLogin()) {
            log.info("⏭️ 跳过真实登录（shouldPerformLogin=false），将使用 getAuthInitScript 兜底");
            return;
        }

        launchBrowserIfNeeded();

        BrowserContext loginContext = null;
        try {
            log.info("===== Suite 级登录开始 =====");

            loginContext = browser.newContext(getContextOptions());
            Page loginPage = loginContext.newPage();

            loginPage.navigate(getBaseUrl());
            loginPage.waitForLoadState(
                    com.microsoft.playwright.options.LoadState.NETWORKIDLE);

            LoginPage login = new LoginPage(loginPage);
            login.login(getTestUsername(), getTestPassword());

            loginContext.storageState(
                    new BrowserContext.StorageStateOptions()
                            .setPath(AUTH_STATE_PATH));
            log.info("💾 登录态已保存到: {}", AUTH_STATE_PATH.toAbsolutePath());

            loginCompleted = true;
            log.info("===== Suite 级登录完成 =====");

        } catch (Exception e) {
            log.error("❌ Suite 级登录失败: {}，将使用 getAuthInitScript 兜底", e.getMessage());
            loginCompleted = false;
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

        // 2. 创建 BrowserContext
        BrowserContext context;
        if (loginCompleted && java.nio.file.Files.exists(AUTH_STATE_PATH)) {
            // 复用真实登录态
            context = browser.newContext(
                    getContextOptions().setStorageStatePath(AUTH_STATE_PATH));
            log.debug("🔐 加载已有登录态");
        } else {
            // 无真实登录态 → 裸创建
            context = browser.newContext(getContextOptions());
            log.debug("📄 裸创建 Context（无登录态）");
        }
        Page page = context.newPage();

        // 3. 如果跳过了真实登录，注入假 token 兜底
        String authScript = getAuthInitScript();
        if (!loginCompleted && authScript != null && !authScript.isBlank()) {
            page.addInitScript(authScript);
            log.debug("🔐 已注入 initScript token");
        }

        threadContext.set(context);
        threadPage.set(page);
    }

    @AfterMethod
    public void baseTearDown(Method method, ITestResult result) {
        String testName = method.getDeclaringClass().getSimpleName()
                + "." + method.getName();

        // 失败截图由 FailureListener.onTestFailure 处理（它在 @AfterMethod 之前触发，Page 存活时截图）

        // 关闭本用例的 BrowserContext
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
