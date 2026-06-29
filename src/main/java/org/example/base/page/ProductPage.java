package org.example.base.page;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * @author Kiko Song
 * @create 2026-06-25 22:06
 */
public class ProductPage extends BasePage {
    // 1.定义TAB容器
    private final Locator tabContainer = page.locator("#merchant-tabs");
    // 2.定义TAB子项的集合定位器
    private final Locator tabItems = tabContainer.locator(".btn-primary btn-sm");
    // 3. 定义列表容器的定位器
    private final Locator cardListContainer = page.locator("#product-list");
    // 4. 定义所有子项（卡片）的集合定位器
    private final Locator cardItems = cardListContainer.locator(".product-card");

    public ProductPage(Page page) {
        super(page);
    }

    // ================= 动态 Tab 封装 =================
    // 点击tab
    public void clickTab(String tabName) {
        // Playwright 原生支持通过文本和角色精准定位，无需遍历
        Locator targetTab = tabItems.filter(new Locator.FilterOptions().setHasText(tabName));
        click(targetTab);
    }


    // 某商品，点击加入购物车
    public void clickAddCar(String productName) {
        // Playwright 原生支持通过文本和角色精准定位，无需遍历
        Locator targetTab = cardItems.filter(new Locator.FilterOptions().setHasText(productName));
        click(targetTab);
    }

    //获取商品名
    public String getProductName(Locator locator) {
        Locator tempLocator = locator.locator(".item-name");
        return tempLocator.textContent();
    }


}
