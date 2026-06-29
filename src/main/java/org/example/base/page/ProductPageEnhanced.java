package org.example.base.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品页封装（增强版）—— 基于 ProductPage 改进.
 *
 * <h3>对应前端 DOM 结构</h3>
 * <pre>
 * &lt;!-- 商家 TAB 切换 --&gt;
 * &lt;div id="merchant-tabs"&gt;
 *   &lt;button class="btn-primary btn-sm"&gt;商家A&lt;/button&gt;   &lt;!-- 当前选中 --&gt;
 *   &lt;button class="btn-outline btn-sm"&gt;商家B&lt;/button&gt;    &lt;!-- 未选中 --&gt;
 * &lt;/div&gt;
 *
 * &lt;!-- 商品列表 --&gt;
 * &lt;div id="product-list"&gt;
 *   &lt;div class="product-card"&gt;
 *     &lt;div class="item-img"&gt;📦&lt;/div&gt;
 *     &lt;div class="item-info"&gt;
 *       &lt;div class="item-name"&gt;商品名称&lt;/div&gt;
 *       &lt;div class="item-price"&gt;¥99.00 库存: 100&lt;/div&gt;
 *     &lt;/div&gt;
 *     &lt;button class="btn-primary btn-sm"&gt;加入购物车&lt;/button&gt;
 *   &lt;/div&gt;
 * &lt;/div&gt;
 * </pre>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * ProductPageEnhanced page = new ProductPageEnhanced(playwrightPage);
 * page.clickMerchantTab("商家A");
 * page.addToCart("商品名");
 * page.addToCart(0);                    // 第1个商品
 * List&lt;ProductInfo&gt; products = page.getAllProducts();  // 遍历
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
public class ProductPageEnhanced extends BasePage {

    // ======================== 页面结构定位 ========================

    /** 商家 TAB 容器 */
    private final Locator merchantTabContainer = page.locator("#merchant-tabs");

    /**
     * 所有 TAB 按钮的集合定位器。
     * <p>
     * <b>注意：</b>不能用 ".btn-primary btn-sm"（那是后代选择器）。
     * 正确的做法是匹配所有 button —— 因为选中态有 btn-primary、非选中态有 btn-outline，
     * 两者共有的类只有 btn-sm，所以选 button 然后通过文本过滤。
     */
    private final Locator allMerchantTabs = merchantTabContainer.locator("button");

    /** 商品列表容器 */
    private final Locator productListContainer = page.locator("#product-list");

    /** 所有商品卡片（.product-card）的集合定位器 */
    private final Locator allProductCards = productListContainer.locator(".product-card");

    /** Toast 消息（页面顶部浮动提示） */
    private final Locator toast = page.locator("#toast");

    // ======================== 构造器 ========================

    public ProductPageEnhanced(Page page) {
        super(page);
    }

    // ======================== 商家 TAB ========================

    /**
     * 切换到指定商家名称的 TAB.
     * <p>
     * 通过文本过滤找到目标按钮并点击，Playwright 会自动等待按钮可点击。
     *
     * @param merchantName 商家名称（模糊匹配）
     */
    public void clickMerchantTab(String merchantName) {
        log.info("🏪 切换到商家 TAB: {}", merchantName);
        // allMerchantTabs 是集合定位器，filter 后还是集合，但 Playwright 默认操作第一个匹配项
        Locator targetTab = allMerchantTabs.filter(
                new Locator.FilterOptions().setHasText(merchantName));
        click(targetTab);
        // 点击后等待商品列表刷新
        waitForPageLoad();
    }

    /**
     * 按索引点击商家 TAB（0-based）.
     */
    public void clickMerchantTab(int index) {
        log.info("🏪 切换到商家 TAB [{}]", index);
        click(allMerchantTabs.nth(index));
        waitForPageLoad();
    }

    /** 获取当前选中的商家 TAB 文本 */
    public String getActiveMerchantName() {
        // 当前选中的 TAB 有 btn-primary 类
        Locator active = merchantTabContainer.locator("button.btn-primary");
        return getText(active);
    }

    /** 获取所有商家名称 */
    public List<String> getAllMerchantNames() {
        int count = allMerchantTabs.count();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            names.add(allMerchantTabs.nth(i).innerText());
        }
        return names;
    }

    // ======================== 商品卡片操作 ========================

    /**
     * ✅ 正确做法：点某商品的"加入购物车"按钮 —— 直接用商品名定位到卡片，
     * 再在卡片内部找按钮。
     *
     * @param productName 商品名称（模糊匹配）
     */
    public void addToCart(String productName) {
        log.info("🛒 加入购物车: {}", productName);
        // 1. 通过商品名定位到目标卡片
        Locator targetCard = findProductCard(productName);
        // 2. 在卡片内找到"加入购物车"按钮并点击
        Locator addBtn = targetCard.locator("button");
        click(addBtn);
        // 3. 等待 toast 提示
        waitForToast();
    }

    /**
     * 按索引添加商品到购物车（0-based）.
     */
    public void addToCart(int index) {
        Locator card = allProductCards.nth(index);
        String name = getProductName(card);
        log.info("🛒 加入购物车 [{}]: {}", index, name);
        click(card.locator("button"));
        waitForToast();
    }

    /** 添加商品到购物车并指定数量（如果前端支持） */
    public void addToCart(String productName, int quantity) {
        for (int i = 0; i < quantity; i++) {
            addToCart(productName);
        }
    }

    // ======================== 商品信息读取 ========================

    /**
     * 获取某张卡片内的商品名称.
     *
     * @param card 单张 .product-card 的 Locator
     * @return 商品名
     */
    public String getProductName(Locator card) {
        // .item-name 是 .product-card 的直接子元素
        return card.locator(".item-name").textContent();
    }

    /**
     * 获取某张卡片内的商品价格字符串（包含"库存:"信息）.
     * <p>
     * 前端渲染格式: "¥99.00 &nbsp; 库存: 100"
     *
     * @param card 单张 .product-card 的 Locator
     * @return 原始价格文本
     */
    public String getPriceText(Locator card) {
        return card.locator(".item-price").textContent();
    }

    /**
     * 解析出纯数字价格.
     */
    public double getPrice(Locator card) {
        String text = getPriceText(card);
        // "¥99.00 库存: 100" → 取开头的数字部分
        String priceStr = text.replace("¥", "").split(" ")[0].trim();
        return Double.parseDouble(priceStr);
    }

    /**
     * 解析库存数量.
     */
    public int getStock(Locator card) {
        String text = getPriceText(card);
        // "¥99.00 库存: 100" → 取冒号后的数字
        try {
            String stockStr = text.split("库存:")[1].trim();
            return Integer.parseInt(stockStr);
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 通过商品名定位到单张卡片.
     * <p>
     * 这里用 allProductCards.filter(hasText) 而非 findByText，
     * 因为 findByText 可能匹配到卡片内部的任意文本节点。
     */
    public Locator findProductCard(String productName) {
        return allProductCards.filter(
                new Locator.FilterOptions().setHasText(productName));
    }

    // ======================== 商品列表遍历 ========================

    /**
     * 获取当前页所有商品信息.
     * <p>
     * 遍历 .product-card 集合，逐张卡片提取名称和价格。
     * 适合做数据驱动断言（如"商品列表中应包含 xxx"）。
     */
    public List<ProductInfo> getAllProducts() {
        int count = allProductCards.count();
        List<ProductInfo> products = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Locator card = allProductCards.nth(i);
            products.add(new ProductInfo(
                    getProductName(card),
                    getPrice(card),
                    getStock(card),
                    card // 保留原始 Locator，方便后续操作
            ));
        }
        log.info("📋 当前商家共 {} 件商品", products.size());
        return products;
    }

    /** 获取商品总数 */
    public int getProductCount() {
        return allProductCards.count();
    }

    /**
     * 判断某个商品是否存在.
     */
    public boolean hasProduct(String productName) {
        return findProductCard(productName).count() > 0;
    }

    // ======================== Toast 提示 ========================

    /**
     * 等待 toast 出现并消失.
     * <p>
     * 前端 toast 逻辑：加入购物车成功后显示 "已加入购物车"，
     * 2 秒后自动隐藏（opacity 变为 0）。
     */
    public void waitForToast() {
        try {
            // toast 出现（opacity 由 0 变 1）
            waitForVisible("#toast");  // CSS selector 版本的 waitForVisible
            log.debug("📢 Toast: {}", toast.innerText());
            // 等 toast 消失再返回（避免遮挡后续操作）
            waitForHidden(toast);
        } catch (Exception e) {
            // 有时 toast 太快消失，捕获异常但不中断流程
            log.debug("Toast 捕获异常（可能已消失）: {}", e.getMessage());
        }
    }

    /**
     * 获取最后一次 toast 文本（用于断言操作结果）.
     */
    public String getToastText() {
        return toast.innerText();
    }

    // ======================== 导航 ========================

    /** 跳转到购物车页 */
    public CartPage goToCart() {
        log.info("🛒 跳转到购物车");
        click(findByText("🛒 购物车"));
        waitForPageLoad();
        return navigateTo(CartPage.class);
    }

    // ======================== 内部数据类 ========================

    /**
     * 商品信息数据传输对象.
     * <p>
     * 把卡片的关键字段抽出来，方便在测试中断言：
     * <pre>{@code
     * List<ProductInfo> products = page.getAllProducts();
     * assertThat(products).extracting(ProductInfo::name).contains("xxx");
     * }</pre>
     */
    public static class ProductInfo {
        private final String name;
        private final double price;
        private final int stock;
        private final Locator cardLocator; // 保留原始定位器，支持后续链式操作

        public ProductInfo(String name, double price, int stock, Locator cardLocator) {
            this.name = name;
            this.price = price;
            this.stock = stock;
            this.cardLocator = cardLocator;
        }

        public String getName() { return name; }
        public double getPrice() { return price; }
        public int getStock() { return stock; }

        /** 该商品卡片的原始 Locator，可继续操作（如点击加入购物车） */
        public Locator getCardLocator() { return cardLocator; }

        @Override
        public String toString() {
            return "ProductInfo{name='" + name + "', price=" + price + ", stock=" + stock + "}";
        }
    }

}
