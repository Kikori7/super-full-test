/**
 * 全局监听器包.
 *
 * <h3>规划中的组件</h3>
 * <ul>
 *   <li><b>全局异常处理</b> — IHookable / ITestListener 实现，
 *       统一捕获和处理测试异常，区分业务异常与框架异常</li>
 *   <li><b>失败截图</b> — ITestListener.onTestFailure 触发，
 *       Playwright 截图 + 附加到 Allure/Extent 报告</li>
 *   <li><b>测试报告增强</b> — 自定义 Reporter，注入额外元数据</li>
 * </ul>
 *
 * @author Kiko Song
 * @since 2026-06-25
 */
package org.example.base.listener;
