package org.example;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;

import java.nio.file.Paths;

/**
 * @author Kiko Song
 * @create 2026-06-09 20:47
 */
public class Demo2Test {
    private Browser browser;
    private Page page;

    public static void main(String[] args) {
        try (Playwright pw = Playwright.create()) {
            Browser bw = pw.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
            BrowserContext browserContext = bw.newContext();
            browserContext.tracing().start(new Tracing.StartOptions()
                    .setScreenshots(true)
                    .setSnapshots(true));
            Page page = browserContext.newPage();

//            // 监听请求开始事件
//            page.onRequest(request -> {
//                System.out.println("Request URL: " + request.url());
//                System.out.println("Request Method: " + request.method());
//            });
//
//            // 监听请求结束事件，获取状态码
//            page.onResponse(response -> {
//                System.out.println("Response Status Code: " + response.status());
//            });

            page.navigate("https://www.baidu.com/");
            page.getByRole(AriaRole.BUTTON,new Page.GetByRoleOptions().setName(" 百度一下 ")).click();
//            page.getByText("武汉榜");
            page.getByText("武汉榜").click();
            browserContext.tracing().stop(new Tracing.StopOptions()
                    .setPath(Paths.get("trace.zip")));
            bw.close();
        }
    }
}
