package org.example.base.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import java.util.ArrayList;
import java.util.List;

/**
 * 购物车页面对象.
 *
 * <h3>对应前端 DOM 结构</h3>
 * <pre>
 * &lt;!-- 每张商家卡片 --&gt;
 * &lt;div class="card"&gt;
 *   &lt;div class="merchant-header"&gt;🏪 商家A&lt;/div&gt;
 *   &lt;div class="cart-item"&gt;
 *     &lt;div class="item-qty"&gt;
 *       &lt;button&gt;−&lt;/button&gt;
 *       &lt;span&gt;2&lt;/span&gt;
 *       &lt;button&gt;+&lt;/button&gt;
 *     &lt;/div&gt;
 *     &lt;button class="btn-danger btn-sm"&gt;删除&lt;/button&gt;
 *   &lt;/div&gt;
 *   &lt;button class="btn-outline btn-sm"&gt;清空该商家&lt;/button&gt;
 * &lt;/div&gt;
 *
 * &lt;!-- 或空购物车 --&gt;
 * &lt;div class="empty"&gt;🛒 购物车空空如也&lt;/div&gt;
 * </pre>
 *
 * @author Kiko Song
 * @since 2026-06-27
 */
public class CartPage extends BasePage {

    // ======================== 页面结构定位 ========================

    /** 所有商家卡片容器 */
    private final Locator allCards = page.locator(".card");

    /** 所有购物车条目 */
    private final Locator allItems = page.locator(".cart-item");

    /** 空购物车提示 */
    private final Locator emptyHint = page.locator(".empty");

    // ======================== 工厂方法 ========================

    /**
     * 从已登录的 Page 直接打开购物车页面.
     *
     * <p>前置条件：调用前已通过 BaseTest 的 getAuthInitScript() 注入 token。
     * 导航到 baseUrl 后，前端自动调 loadCart() 渲染购物车，本方法等待渲染完成后返回。
     *
     * @param page   已注入登录 token 的 Page
     * @param baseUrl 被测应用地址
     * @return 等待购物车渲染完成的 CartPage
     */
    public static CartPage open(Page page, String baseUrl) {
        page.navigate(baseUrl);
        page.waitForLoadState(LoadState.NETWORKIDLE);
        // 前端加载后自动调 loadCart() → 渲染 .cart-item
        page.waitForSelector(".cart-item", new Page.WaitForSelectorOptions()
                .setTimeout(10_000));
        return new CartPage(page);
    }

    // ======================== 构造器 ========================

    public CartPage(Page page) {
        super(page);
    }

    // ======================== 按钮可见性检查 ========================

    /**
     * 购物车为空时是否显示空状态提示.
     */
    public boolean isEmptyCartDisplayed() {
        return isVisible(emptyHint);
    }

    /**
     * 获取指定商品的数量减按钮（−）.
     *
     * @param productName 商品名称
     */
    public Locator getDecreaseButton(String productName) {
        return findItem(productName).locator(".item-qty button").first();
    }

    /**
     * 获取指定商品的数量加按钮（+）.
     *
     * @param productName 商品名称
     */
    public Locator getIncreaseButton(String productName) {
        return findItem(productName).locator(".item-qty button").last();
    }

    /**
     * 获取指定商品的删除按钮.
     *
     * @param productName 商品名称
     */
    public Locator getDeleteButton(String productName) {
        return findItem(productName).locator("button.btn-danger");
    }

    /**
     * 获取指定商家的"清空该商家"按钮.
     *
     * @param merchantName 商家名称
     */
    public Locator getClearMerchantButton(String merchantName) {
        return findMerchantCard(merchantName).locator("button.btn-outline");
    }

    // ======================== 按钮可见性批量检查 ========================

    /** 遍历所有条目的所有按钮，返回每个按钮的可见性 */
    public List<ButtonStatus> checkAllButtons() {
        List<ButtonStatus> results = new ArrayList<>();
        int itemCount = allItems.count();
        for (int i = 0; i < itemCount; i++) {
            Locator item = allItems.nth(i);
            String productName = getItemName(item);

            // 减按钮
            Locator decrease = item.locator(".item-qty button").first();
            results.add(new ButtonStatus(productName, "−（减数量）", decrease, decrease.isVisible()));

            // 加按钮
            Locator increase = item.locator(".item-qty button").last();
            results.add(new ButtonStatus(productName, "+（加数量）", increase, increase.isVisible()));

            // 删除按钮
            Locator delete = item.locator("button.btn-danger");
            results.add(new ButtonStatus(productName, "删除", delete, delete.isVisible()));
        }
        return results;
    }

    /** 检查所有商家的"清空该商家"按钮 */
    public List<ButtonStatus> checkAllClearMerchantButtons() {
        List<ButtonStatus> results = new ArrayList<>();
        int cardCount = allCards.count();
        for (int i = 0; i < cardCount; i++) {
            Locator card = allCards.nth(i);
            String merchantName = getMerchantName(card);
            Locator clearBtn = card.locator("button.btn-outline");
            results.add(new ButtonStatus(merchantName, "清空该商家", clearBtn, clearBtn.isVisible()));
        }
        return results;
    }

    // ======================== 按钮数量检查 ========================

    /** 购物车中商品条目数 */
    public int getItemCount() {
        return allItems.count();
    }

    /** − 按钮总数（每个商品一个） */
    public int getDecreaseButtonCount() {
        return allItems.count(); // 每行一个 −
    }

    /** + 按钮总数（每个商品一个） */
    public int getIncreaseButtonCount() {
        return allItems.count(); // 每行一个 +
    }

    /** 删除按钮总数（每个商品一个） */
    public int getDeleteButtonCount() {
        return (int) allItems.locator("button.btn-danger").count();
    }

    /** "清空该商家" 按钮总数（每个商家一个） */
    public int getClearMerchantButtonCount() {
        return (int) allCards.locator("button.btn-outline").count();
    }

    // ======================== 购物车汇总信息 ========================

    /** 获取汇总栏的商品总件数文本 */
    public String getTotalCountText() {
        return page.locator(".total-bar span").innerText();
    }

    // ======================== 操作 ========================

    /** 点 − 减少数量 */
    public void decreaseQuantity(String productName) {
        log.info("➖ 减少数量: {}", productName);
        click(getDecreaseButton(productName));
    }

    /** 点 + 增加数量 */
    public void increaseQuantity(String productName) {
        log.info("➕ 增加数量: {}", productName);
        click(getIncreaseButton(productName));
    }

    /** 点删除 */
    public void deleteItem(String productName) {
        log.info("🗑 删除: {}", productName);
        click(getDeleteButton(productName));
    }

    /** 点清空该商家 */
    public void clearMerchant(String merchantName) {
        log.info("🧹 清空商家: {}", merchantName);
        click(getClearMerchantButton(merchantName));
    }

    /** 跳转回商品页 */
    public void goToProducts() {
        log.info("📦 切换到商品 TAB");
        click(findByText("📦 商品"));
        waitForPageLoad();
    }

    // ======================== 辅助 ========================

    /** 通过商品名定位到 cart-item */
    private Locator findItem(String productName) {
        return allItems.filter(new Locator.FilterOptions().setHasText(productName));
    }

    /** 通过商家名定位到 card */
    private Locator findMerchantCard(String merchantName) {
        return allCards.filter(new Locator.FilterOptions().setHasText(merchantName));
    }

    /** 获取 cart-item 里的商品名 */
    private String getItemName(Locator item) {
        return item.locator(".item-name").innerText();
    }

    /** 获取 card 里的商家名 */
    private String getMerchantName(Locator card) {
        return card.locator(".merchant-header").innerText().replace("🏪 ", "");
    }

    // ======================== 按钮状态 DTO ========================

    /** 按钮可见性检查结果 */
    public static class ButtonStatus {
        private final String owner;      // 所属商品名或商家名
        private final String buttonType; // 按钮类型
        private final Locator locator;   // 原始定位器
        private final boolean visible;

        public ButtonStatus(String owner, String buttonType, Locator locator, boolean visible) {
            this.owner = owner;
            this.buttonType = buttonType;
            this.locator = locator;
            this.visible = visible;
        }

        public String getOwner() { return owner; }
        public String getButtonType() { return buttonType; }
        public Locator getLocator() { return locator; }
        public boolean isVisible() { return visible; }

        @Override
        public String toString() {
            return (visible ? "✅" : "❌") + " [" + owner + "] " + buttonType;
        }
    }
}
