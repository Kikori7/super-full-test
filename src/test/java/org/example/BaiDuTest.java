package org.example;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * @author Kiko Song
 * @create 2026-04-19 16:39
 */
public class BaiDuTest {
    private Browser browser;
    private Page page;
    @BeforeClass
    public void setUp() {
        Playwright playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
    }

    @Test
    public void testHomePageTitle() {
        page.navigate("https://baidu.com");
        String title = page.title();
        Assert.assertEquals(title, "百度一下，你就知道");
    }

    @AfterClass
    public void tearDown() {
        if (browser != null) {
            browser.close();
        }
    }
}
