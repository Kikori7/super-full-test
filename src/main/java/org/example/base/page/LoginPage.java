package org.example.base.page;

import com.microsoft.playwright.Page;

/**
 * 登录页 — 对应前端 auth-form 登录表单.
 *
 * <h3>DOM 结构</h3>
 * <pre>
 * &lt;div id="auth-form"&gt;
 *   &lt;input id="l-user" placeholder="用户名"&gt;
 *   &lt;input id="l-pass" type="password" placeholder="密码"&gt;
 *   &lt;button class="btn-primary"&gt;登 录&lt;/button&gt;
 * &lt;/div&gt;
 * &lt;!-- 登录成功后，.tabs 出现 --&gt;
 * &lt;div class="tabs"&gt;...&lt;/div&gt;
 * </pre>
 *
 * @author Kiko Song
 * @since 2026-06-27
 */
public class LoginPage extends BasePage {

    // 登录表单元素
    private final String usernameSelector = "#l-user";
    private final String passwordSelector = "#l-pass";
    private final String loginButtonSelector = "#auth-form button.btn-primary";

    // 登录成功标志 — 顶部 TAB 栏出现
    private final String tabsSelector = ".tabs";

    public LoginPage(Page page) {
        super(page);
    }

    /**
     * 执行登录操作（真实 UI 交互，非 mock）.
     *
     * @param username 用户名
     * @param password 密码
     */
    public void login(String username, String password) {
        log.info("🔐 登录: {}", username);

        // 填写用户名
        fill(find(usernameSelector), username);
        // 填写密码
        fill(find(passwordSelector), password);
        // 点击登录按钮
        click(find(loginButtonSelector));

        // 等待登录完成 — TAB 栏出现表示登录成功
        waitForVisible(tabsSelector);
        log.info("✅ 登录成功 — TAB 栏已出现");
    }

    /** 是否已显示登录表单 */
    public boolean isLoginFormVisible() {
        return isVisible(find(loginButtonSelector));
    }
}
