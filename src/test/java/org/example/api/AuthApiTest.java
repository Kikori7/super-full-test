package org.example.api;

import io.restassured.response.Response;
import org.example.api.base.ApiTestBase;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * 认证接口测试 — 登录成功/失败、鉴权拦截.
 *
 * @author Kiko Song
 * @since 2026-08-25
 */
public class AuthApiTest extends ApiTestBase {

    @Test(description = "登录成功：HTTP 200 + code=200 + 返回非空 token")
    public void testLoginSuccess() {
        Map<String, String> body = new HashMap<>();
        body.put("username", getUsername());
        body.put("password", getPassword());

        Response resp = given()
                .contentType("application/json")
                .body(body)
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("code", equalTo(200))
                .extract().response();

        String token = resp.jsonPath().getString("data.token");
        Assert.assertNotNull(token, "登录成功应返回 token");
        Assert.assertFalse(token.isEmpty(), "token 不应为空");
        Assert.assertEquals(resp.jsonPath().getString("data.username"), getUsername());
    }

    @Test(description = "密码错误：HTTP 401 + code=401")
    public void testLoginWrongPassword() {
        Map<String, String> body = new HashMap<>();
        body.put("username", getUsername());
        body.put("password", "wrong-password-123");

        given()
                .contentType("application/json")
                .body(body)
                .post("/api/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo(401));
    }

    @Test(description = "未带 token 访问受保护接口：HTTP 403")
    public void testAccessWithoutToken() {
        given()
                .get("/api/cart/list")
                .then()
                .statusCode(403);
    }
}
