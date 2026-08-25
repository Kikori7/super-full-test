package org.example.api.base;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * API 测试基类 — RestAssured 配置 + 登录态(token)管理.
 *
 * <p>与 UI 的 {@code BaseTest} 解耦：API 测试不碰浏览器，直接真打后端 HTTP 接口。
 * 两者共享的是"被测系统地址 + 测试账号 + 重试机制 + 邮件报告"这些框架层能力，
 * 但 UI 的失败截图(FailureListener)依赖 Playwright Page，API 套件不注册它。
 *
 * <h3>登录态</h3>
 * {@code @BeforeClass} 真实登录一次拿到 token，后续用例通过 {@link #request()} 自动带上。
 *
 * <p>子类可覆盖 {@link #getBaseUrl()} / {@link #getUsername()} / {@link #getPassword()}。
 *
 * @author Kiko Song
 * @since 2026-08-25
 */
public abstract class ApiTestBase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** 登录后缓存的 token，整个测试类共享 */
    private static String token;

    // ======================== 子类可覆盖配置 ========================

    /** 被测系统地址。优先级：{@code -Dbase.url} > 环境变量 {@code BASE_URL} > 默认值 */
    protected String getBaseUrl() {
        String prop = System.getProperty("base.url");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("BASE_URL");
        if (env != null && !env.isBlank()) return env;
        return "http://localhost:8080";
    }

    /** 测试账号。优先级：{@code -Dtest.username} > 环境变量 {@code TEST_USERNAME} > 默认值 */
    protected String getUsername() {
        String prop = System.getProperty("test.username");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("TEST_USERNAME");
        if (env != null && !env.isBlank()) return env;
        return "Kiko";
    }

    /** 测试密码。优先级：{@code -Dtest.password} > 环境变量 {@code TEST_PASSWORD} > 默认值 */
    protected String getPassword() {
        String prop = System.getProperty("test.password");
        if (prop != null && !prop.isBlank()) return prop;
        String env = System.getenv("TEST_PASSWORD");
        if (env != null && !env.isBlank()) return env;
        return "huihui123456789+";
    }

    // ======================== 生命周期 ========================

    @BeforeClass
    public void apiSetUp() {
        RestAssured.baseURI = getBaseUrl();
        token = login(getUsername(), getPassword());
        log.info("🔐 API 登录成功，token 已缓存");
    }

    // ======================== 公共能力 ========================

    /** 带登录态 + JSON 内容类型的请求模板 */
    protected RequestSpecification request() {
        return given()
                .contentType("application/json")
                .header("Authorization", "Bearer " + token);
    }

    /** 登录并返回 token（供 {@link #apiSetUp()} 复用） */
    protected String login(String username, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        return given()
                .contentType("application/json")
                .body(body)
                .post("/api/auth/login")
                .then().statusCode(200)
                .extract().jsonPath().getString("data.token");
    }

    protected String getToken() {
        return token;
    }
}
