package org.example;

import com.microsoft.playwright.Page;
import org.example.base.page.CartPage;
import org.example.base.page.CartPage.ButtonStatus;
import org.example.base.test.BaseTest;
import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.List;

/**
 * 购物车按钮显示测试.
 *
 * <h3>登录态管理</h3>
 * {@link BaseTest#suiteLogin()} 在 @BeforeSuite 中执行一次真实登录，
 * 保存 storage state 到 target/auth-state.json。
 * 后续每个 @Test 创建 BrowserContext 时自动加载该文件 → 无需重复登录。
 *
 * <h3>API Mock</h3>
 * 购物车 API 仍然 mock（后端无测试数据），但登录是真实的。
 *
 * @author Kiko Song
 * @since 2026-06-27
 */
public class CartButtonDisplayTest extends BaseTest {

    // ======================== 测试账号（覆盖 BaseTest 默认值） ========================

    @Override
    protected String getTestUsername() {
        return "testuser";          // ← 换成你后端真实的测试账号
    }

    @Override
    protected String getTestPassword() {
        return "123456";            // ← 换成密码
    }

    // ======================== Mock 数据（购物车列表） ========================

    private static final String MOCK_CART_JSON =
        "{\"code\":200,\"data\":{\"totalCount\":3,\"groups\":["
      + "{\"merchantId\":1,\"merchantName\":\"测试商家A\",\"items\":["
      + "{\"productId\":101,\"productName\":\"iPhone 15\",\"price\":5999,\"quantity\":2,\"subtotal\":11998},"
      + "{\"productId\":102,\"productName\":\"AirPods\",\"price\":999,\"quantity\":1,\"subtotal\":999}"
      + "]},"
      + "{\"merchantId\":2,\"merchantName\":\"测试商家B\",\"items\":["
      + "{\"productId\":201,\"productName\":\"充电器\",\"price\":149,\"quantity\":1,\"subtotal\":149}"
      + "]}"
      + "]}}";

    // ======================== Mock API ========================

    @BeforeMethod
    public void setUpApiMocks() {
        Page page = getPage();

        page.route("**/api/cart/list", route -> {
            route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(MOCK_CART_JSON));
        });

        page.route("**/api/merchants", route -> {
            route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody("[]"));
        });
    }

    // ======================== 测试用例 ========================

    @Test(description = "购物车按钮数量 = 商品数量 × 3 + 商家数量")
    public void testButtonCounts() {
        CartPage cart = CartPage.open(getPage(), getBaseUrl());

        Assert.assertEquals(cart.getItemCount(), 3, "应有 3 件商品");
        Assert.assertEquals(cart.getDecreaseButtonCount(), 3);
        Assert.assertEquals(cart.getIncreaseButtonCount(), 3);
        Assert.assertEquals(cart.getDeleteButtonCount(), 3);
        Assert.assertEquals(cart.getClearMerchantButtonCount(), 2);
    }

    @Test(description = "遍历所有按钮，逐个验证可见性")
    public void testAllButtonsVisible() {
        CartPage cart = CartPage.open(getPage(), getBaseUrl());

        List<ButtonStatus> itemButtons = cart.checkAllButtons();
        System.out.println("\n===== 购物车商品按钮检查 =====");
        for (ButtonStatus s : itemButtons) {
            System.out.println("  " + s);
            Assert.assertTrue(s.isVisible(),
                    s.getOwner() + " 的 " + s.getButtonType() + " 按钮不可见");
        }
        Assert.assertEquals(itemButtons.size(), 9);

        List<ButtonStatus> clearButtons = cart.checkAllClearMerchantButtons();
        System.out.println("\n===== 商家清空按钮检查 =====");
        for (ButtonStatus s : clearButtons) {
            System.out.println("  " + s);
            Assert.assertTrue(s.isVisible(),
                    s.getOwner() + " 的 " + s.getButtonType() + " 按钮不可见");
        }
        Assert.assertEquals(clearButtons.size(), 2);
    }

    @Test(description = "按商品名定位，验证特定商品的按钮")
    public void testSpecificItemButtons() {
        CartPage cart = CartPage.open(getPage(), getBaseUrl());

        Assert.assertTrue(cart.getDecreaseButton("iPhone 15").isVisible());
        Assert.assertTrue(cart.getIncreaseButton("iPhone 15").isVisible());
        Assert.assertTrue(cart.getDeleteButton("iPhone 15").isVisible());

        Assert.assertTrue(cart.getDecreaseButton("AirPods").isVisible());
        Assert.assertTrue(cart.getIncreaseButton("AirPods").isVisible());
        Assert.assertTrue(cart.getDeleteButton("AirPods").isVisible());

        Assert.assertTrue(cart.getDecreaseButton("充电器").isVisible());
        Assert.assertTrue(cart.getIncreaseButton("充电器").isVisible());
        Assert.assertTrue(cart.getDeleteButton("充电器").isVisible());

        Assert.assertTrue(cart.getClearMerchantButton("测试商家A").isVisible());
        Assert.assertTrue(cart.getClearMerchantButton("测试商家B").isVisible());
    }

    @Test(description = "汇总栏正确显示总件数")
    public void testTotalCountDisplayed() {
        CartPage cart = CartPage.open(getPage(), getBaseUrl());
        Assert.assertEquals(cart.getTotalCountText(), "3");
    }
}
