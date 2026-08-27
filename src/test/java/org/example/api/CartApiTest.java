package org.example.api;

import io.restassured.response.Response;
import org.example.api.base.ApiTestBase;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.equalTo;

/**
 * 购物车接口测试 — 按接口拆分的原子用例（真打后端 + Redis）.
 *
 * <h3>原子用例设计</h3>
 * <ul>
 *   <li>{@code @BeforeClass} 一次性数据发现：拿真实商家/商品/库存，不硬编码 id</li>
 *   <li>{@code @BeforeMethod} 清空所有商家购物车，保证每个用例独立、可重复跑</li>
 *   <li>每个用例只验证一个接口的一个行为，失败可精准定位</li>
 * </ul>
 *
 * @author Kiko Song
 * @since 2026-08-25
 */
public class CartApiTest extends ApiTestBase {

    // ======================== 数据发现 ========================

    private List<Long> merchantIds;
    private long merchantId;
    private long productId;
    private String productName;
    private int stock;

    @BeforeClass
    public void discoverData() {
        merchantIds = request().get("/api/merchants").jsonPath().getList("data.id", Long.class);
        Assert.assertFalse(merchantIds.isEmpty(), "后端应有商家数据");

        merchantId = merchantIds.get(0);
        Response products = request().get("/api/products?merchantId=" + merchantId);
        productId = products.jsonPath().getLong("data[0].id");
        productName = products.jsonPath().getString("data[0].name");
        stock = products.jsonPath().getInt("data[0].stock");
        log.info("数据发现: 商家 id={}, 商品 {} (id={}, 库存={})", merchantId, productName, productId, stock);
    }

    /** 每个用例前清空所有商家购物车，保证用例独立幂等 */
    @BeforeMethod
    public void clearCart() {
        for (Long mid : merchantIds) {
            request().delete("/api/cart/clear/" + mid).then().statusCode(200);
        }
    }

    // ======================== 请求辅助 ========================

    private Response addToCart(long productId, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        return request().body(body).post("/api/cart/add");
    }

    private Response updateQuantity(long productId, int quantity) {
        Map<String, Object> body = new HashMap<>();
        body.put("productId", productId);
        body.put("quantity", quantity);
        return request().body(body).put("/api/cart/update");
    }

    private int totalCount() {
        return request().get("/api/cart/list").jsonPath().getInt("data.totalCount");
    }

    // ======================== 加购 ========================

    @Test(description = "加购成功：200 + 返回商品 id/数量正确")
    public void testAddItemSuccess() {
        Response resp = addToCart(productId, 2);
        resp.then().statusCode(200).body("code", equalTo(200));
        Assert.assertEquals(resp.jsonPath().getLong("data.productId"), productId);
        Assert.assertEquals(resp.jsonPath().getInt("data.quantity"), 2);
        Assert.assertEquals(totalCount(), 2);
    }

    @Test(description = "重复加购同一商品：数量累加")
    public void testAddQuantityAccumulates() {
        addToCart(productId, 2).then().statusCode(200);
        addToCart(productId, 3).then().statusCode(200);
        Assert.assertEquals(totalCount(), 5, "重复加购应累加：2+3=5");
    }

    @Test(description = "加购超库存：409")
    public void testAddStockExceeded() {
        addToCart(productId, stock + 1)
                .then().statusCode(409).body("code", equalTo(409));
    }

    @Test(description = "加购不存在的商品：404")
    public void testAddNonexistentProduct() {
        addToCart(999999L, 1)
                .then().statusCode(404).body("code", equalTo(404));
    }

    // ======================== 改数量 ========================

    @Test(description = "改数量成功：数量被覆盖（非累加）")
    public void testUpdateSuccess() {
        addToCart(productId, 2).then().statusCode(200);
        updateQuantity(productId, 5).then().statusCode(200).body("code", equalTo(200));
        Assert.assertEquals(totalCount(), 5, "改数量后应为 5（直接覆盖）");
    }

    @Test(description = "改数量但商品不在购物车：404")
    public void testUpdateNotInCart() {
        updateQuantity(productId, 5)
                .then().statusCode(404).body("code", equalTo(404));
    }

    @Test(description = "改数量超库存：409")
    public void testUpdateStockExceeded() {
        addToCart(productId, 1).then().statusCode(200);
        updateQuantity(productId, stock + 1)
                .then().statusCode(409).body("code", equalTo(409));
    }

    // ======================== 删除 ========================

    @Test(description = "删除成功：200，删后购物车为空")
    public void testRemoveSuccess() {
        addToCart(productId, 2).then().statusCode(200);
        request().delete("/api/cart/remove/" + productId)
                .then().statusCode(200).body("code", equalTo(200));
        Assert.assertEquals(totalCount(), 0, "删除后购物车应为空");
    }

    @Test(description = "删除不在购物车的商品：404")
    public void testRemoveNotInCart() {
        request().delete("/api/cart/remove/" + productId)
                .then().statusCode(404).body("code", equalTo(404));
    }

    // ======================== 清空 ========================

    @Test(description = "清空商家成功：200，清空后购物车为空")
    public void testClearMerchant() {
        addToCart(productId, 2).then().statusCode(200);
        request().delete("/api/cart/clear/" + merchantId)
                .then().statusCode(200).body("code", equalTo(200));
        Assert.assertEquals(totalCount(), 0);
    }

    @Test(description = "空车清空：幂等返回 200")
    public void testClearEmptyCart() {
        request().delete("/api/cart/clear/" + merchantId)
                .then().statusCode(200).body("code", equalTo(200));
    }

    // ======================== 列表 / 计数 ========================

    @Test(description = "空车列表：totalCount=0")
    public void testListEmptyCart() {
        Assert.assertEquals(totalCount(), 0, "空车 totalCount 应为 0");
    }

    @Test(description = "计数接口：加购后 count 正确")
    public void testCartCount() {
        addToCart(productId, 3).then().statusCode(200);
        int count = request().get("/api/cart/count").jsonPath().getInt("data.totalCount");
        Assert.assertEquals(count, 3);
    }
}
