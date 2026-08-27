package org.example.base.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.collections.Lists;
import org.testng.xml.XmlSuite;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 测试报告邮件监听器 — 全部测试完成后自动发送邮件报告.
 *
 * <h3>零依赖 SMTP</h3>
 * 仅使用 JDK 内置 {@link SSLSocket} 直连 QQ 邮箱 SMTP（SSL 465），
 * 不引入 javax.mail / spring-mail 等额外依赖。
 *
 * <h3>配置文件</h3>
 * 按优先级读取 email.properties：<ol>
 *   <li>系统属性 {@code email.config.path}</li>
 *   <li>当前目录 {@code ./email.properties}（Docker mount 友好）</li>
 *   <li>classpath {@code email.properties}（本地开发默认）</li>
 * </ol>
 *
 * <h3>挂载方式</h3>
 * <pre>{@code
 * testng.xml:
 *   <listener class-name="org.example.base.listener.ReportEmailListener"/>
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-08-04
 */
public class ReportEmailListener implements IReporter {

    private static final Logger log = LoggerFactory.getLogger(ReportEmailListener.class);

    // ======================== 配置加载 ========================

    static Properties loadConfig() {
        Properties props = new Properties();

        // 1. 系统属性指定路径
        String explicitPath = System.getProperty("email.config.path");
        if (explicitPath != null && loadFromFile(props, explicitPath)) {
            log.info("📧 邮件配置来源: 系统属性 email.config.path={}", explicitPath);
        }
        // 2. 当前目录
        else if (loadFromFile(props, "./email.properties")) {
            log.info("📧 邮件配置来源: ./email.properties");
        }
        // 3. classpath
        else if (loadFromClasspath(props, "email.properties")) {
            log.info("📧 邮件配置来源: classpath email.properties");
        }
        else {
            log.warn("📧 未找到 email.properties，邮件发送已禁用");
        }

        return props;
    }

    private static boolean loadFromFile(Properties props, String path) {
        File f = new File(path);
        if (!f.exists()) return false;
        try (FileInputStream fis = new FileInputStream(f);
             InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8)) {
            props.load(isr);
            return true;
        } catch (IOException e) {
            log.warn("读取配置文件失败: {} - {}", path, e.getMessage());
            return false;
        }
    }

    private static boolean loadFromClasspath(Properties props, String resource) {
        try (InputStream is = ReportEmailListener.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (is == null) return false;
            props.load(is);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ======================== IReporter ========================

    @Override
    public void generateReport(List<XmlSuite> xmlSuites,
                               List<ISuite> suites,
                               String outputDirectory) {
        try {
            Properties config = loadConfig();
            if (!"true".equalsIgnoreCase(config.getProperty("email.enabled", "false"))) {
                log.info("📧 邮件发送未启用（email.enabled != true）");
                return;
            }

            Stats stats = collectStats(suites);
            String html = buildHtml(stats);
            send(config, html, stats);
        } catch (Exception e) {
            log.error("📧 邮件报告发送异常: {}", e.getMessage(), e);
        }
    }

    // ======================== 统计收集 ========================

    private Stats collectStats(List<ISuite> suites) {
        Stats stats = new Stats();

        for (ISuite suite : suites) {
            for (ISuiteResult sr : suite.getResults().values()) {
                ITestContext ctx = sr.getTestContext();
                stats.total   += ctx.getAllTestMethods().length;
                stats.passed  += ctx.getPassedTests().size();
                stats.failed  += ctx.getFailedTests().size();
                stats.skipped += ctx.getSkippedTests().size();

                // 起始结束时间
                if (stats.startDate == null ||
                        (ctx.getStartDate() != null && ctx.getStartDate().before(stats.startDate))) {
                    stats.startDate = ctx.getStartDate();
                }
                if (stats.endDate == null ||
                        (ctx.getEndDate() != null && ctx.getEndDate().after(stats.endDate))) {
                    stats.endDate = ctx.getEndDate();
                }

                // 失败详情
                for (ITestResult r : ctx.getFailedTests().getAllResults()) {
                    stats.failures.add(new FailDetail(r));
                }
                // 全部用例详情（用于明细表）
                for (ITestResult r : ctx.getPassedTests().getAllResults()) {
                    stats.allResults.add(r);
                }
                for (ITestResult r : ctx.getFailedTests().getAllResults()) {
                    stats.allResults.add(r);
                }
                for (ITestResult r : ctx.getSkippedTests().getAllResults()) {
                    stats.allResults.add(r);
                }
            }
        }
        return stats;
    }

    // ======================== HTML 构建 ========================

    private String buildHtml(Stats s) {
        String passRate = s.total > 0
                ? String.format("%.1f%%", s.passed * 100.0 / s.total) : "N/A";
        String color = s.failed > 0 ? "#e74c3c" : "#27ae60";
        String verdict = s.failed > 0 ? "❌ 有失败用例" : "✅ 全部通过";
        String now = beijingTimeFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String duration = "";
        if (s.startDate != null && s.endDate != null) {
            long sec = Duration.between(s.startDate.toInstant(), s.endDate.toInstant()).getSeconds();
            if (sec < 60) duration = sec + "s";
            else duration = (sec / 60) + "m " + (sec % 60) + "s";
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>")
            .append("body{font-family:'Microsoft YaHei','PingFang SC',sans-serif;color:#333;max-width:700px;margin:0 auto;}")
            .append(".card{border:1px solid #e0e0e0;border-radius:8px;padding:20px;margin:16px 0;}")
            .append(".big-num{font-size:48px;font-weight:bold;color:").append(color).append(";}")
            .append(".label{color:#999;font-size:13px;}")
            .append(".stat-box{display:inline-block;width:80px;text-align:center;margin:10px 24px 10px 0;}")
            .append(".stat-num{font-size:28px;font-weight:bold;}")
            .append("table{border-collapse:collapse;width:100%;margin:16px 0;font-size:13px;}")
            .append("th,td{padding:8px 12px;text-align:left;border-bottom:1px solid #eee;}")
            .append("th{background:#f5f6fa;color:#666;font-weight:600;}")
            .append(".PASS{color:#27ae60;}.FAIL{color:#e74c3c;background:#fff5f5;}.SKIP{color:#f39c12;}")
            .append(".footer{color:#aaa;font-size:12px;margin-top:24px;text-align:center;}")
            .append(".fail-reason{font-size:12px;color:#999;margin-left:8px;}")
            .append("</style></head><body>")
            .append("<h2>🛒 购物车自动化测试报告</h2>")
            .append("<p style=\"color:#666;\">执行时间：").append(now).append(" &nbsp;|&nbsp; 耗时：").append(duration).append("</p>")

            // 大盘卡片
            .append("<div class=\"card\"><table><tr>")
            .append("<td style=\"width:140px;\"><div class=\"big-num\">").append(passRate).append("</div><div class=\"label\">通过率</div></td>")
            .append("<td>")
            .append("<div class=\"stat-box\"><div class=\"stat-num\" style=\"color:#333;\">").append(s.total).append("</div><div class=\"label\">总计</div></div>")
            .append("<div class=\"stat-box\"><div class=\"stat-num\" style=\"color:#27ae60;\">").append(s.passed).append("</div><div class=\"label\">✅ 通过</div></div>")
            .append("<div class=\"stat-box\"><div class=\"stat-num\" style=\"color:#e74c3c;\">").append(s.failed).append("</div><div class=\"label\">❌ 失败</div></div>")
            .append("<div class=\"stat-box\"><div class=\"stat-num\" style=\"color:#f39c12;\">").append(s.skipped).append("</div><div class=\"label\">⏭ 跳过</div></div>")
            .append("</td></tr></table>")
            .append("<p style=\"margin-top:12px;font-size:18px;\">").append(verdict).append("</p>")
            .append("</div>");

        // 失败用例
        if (!s.failures.isEmpty()) {
            html.append("<h3>❌ 失败用例</h3><table>")
                .append("<tr><th>用例</th><th>异常</th></tr>");
            for (FailDetail f : s.failures) {
                html.append("<tr class=\"FAIL\"><td>")
                    .append(escape(f.className)).append("#").append(escape(f.methodName))
                    .append("<br><span class=\"fail-reason\">").append(escape(f.description)).append("</span></td>")
                    .append("<td>").append(escape(f.exceptionMsg)).append("</td></tr>");
            }
            html.append("</table>");
        }

        // 全部用例明细
        if (!s.allResults.isEmpty()) {
            html.append("<h3>📋 用例明细</h3><table>")
                .append("<tr><th>类</th><th>方法</th><th>描述</th><th>状态</th></tr>");
            for (ITestResult r : s.allResults) {
                String status = statusLabel(r);
                html.append("<tr class=\"").append(status).append("\">")
                    .append("<td>").append(r.getTestClass().getName()).append("</td>")
                    .append("<td>").append(r.getMethod().getMethodName()).append("</td>")
                    .append("<td>").append(descOrDash(r)).append("</td>")
                    .append("<td class=\"").append(status).append("\"><b>").append(status).append("</b></td>")
                    .append("</tr>");
            }
            html.append("</table>");
        }

        html.append("<div class=\"footer\">由购物车自动化测试系统自动发送 &nbsp;·&nbsp; ").append(now).append("</div>")
            .append("</body></html>");
        return html.toString();
    }

    // ======================== SMTP 发送 ========================

    private void send(Properties config, String html, Stats stats) {
        String host = config.getProperty("email.smtp.host", "smtp.qq.com");
        int port = Integer.parseInt(config.getProperty("email.smtp.port", "465"));
        String from = config.getProperty("email.from");
        String password = config.getProperty("email.password");
        String toRaw = config.getProperty("email.to");

        if (from == null || from.contains("你的QQ号") ||
            password == null || password.contains("授权码")) {
            log.warn("📧 邮箱配置未填写，跳过发送。请编辑 email.properties");
            return;
        }

        List<String> recipients = Arrays.stream(toRaw.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        String now = beijingTimeFormat("MM-dd HH:mm").format(new Date());
        String prefix = stats.failed > 0 ? "❌" : "✅";
        String subject = base64Encode(
                String.format("%s 购物车测试报告 %s — 通过 %d/%d",
                        prefix, now, stats.passed, stats.total));

        try {
            SSLSocket socket = (SSLSocket) SSLSocketFactory.getDefault()
                    .createSocket(host, port);
            socket.startHandshake();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            // 等待服务器 greeting
            readResponse(in, 220);

            // EHLO
            cmd(out, in, "EHLO test", 250);

            // AUTH LOGIN
            cmd(out, in, "AUTH LOGIN", 334);
            cmd(out, in, base64Encode(from), 334);
            cmd(out, in, base64Encode(password), 235);

            // MAIL FROM
            cmd(out, in, "MAIL FROM:<" + from + ">", 250);

            // RCPT TO
            for (String to : recipients) {
                cmd(out, in, "RCPT TO:<" + to + ">", 250);
            }

            // DATA
            cmd(out, in, "DATA", 354);

            // 邮件内容
            out.print("From: " + from + "\r\n");
            out.print("To: " + String.join(", ", recipients) + "\r\n");
            out.print("Subject: =?UTF-8?B?" + subject + "?=\r\n");
            out.print("MIME-Version: 1.0\r\n");
            out.print("Content-Type: text/html; charset=UTF-8\r\n");
            out.print("\r\n");
            out.print(html + "\r\n");
            out.print(".\r\n");
            out.flush();
            readResponse(in, 250);

            // QUIT
            cmd(out, in, "QUIT", 221);

            socket.close();
            log.info("📧 邮件已发送 → {}", recipients);

        } catch (Exception e) {
            log.error("📧 邮件发送失败: {}", e.getMessage(), e);
        }
    }

    // ======================== SMTP 辅助 ========================

    private void cmd(PrintWriter out, BufferedReader in, String command, int expectedCode)
            throws IOException {
        out.print(command + "\r\n");
        out.flush();
        readResponse(in, expectedCode);
    }

    private void readResponse(BufferedReader in, int expectedCode) throws IOException {
        String line = in.readLine();
        if (line == null) throw new IOException("SMTP 服务器无响应");
        int code = Integer.parseInt(line.substring(0, 3));
        if (code != expectedCode) {
            // 多行响应：继续读到非续行
            while (line != null && line.length() > 3 && line.charAt(3) == '-') {
                line = in.readLine();
            }
            if (code != expectedCode) {
                // 不抛异常，让上层决定
            }
        }
    }

    private static String base64Encode(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 返回使用北京时间(Asia/Shanghai)的日期格式化器.
     *
     * <p>容器默认 UTC 时区，若直接用 {@code new SimpleDateFormat(...).format(new Date())}，
     * 报告里的"执行时间"会差 8 小时（早上 7 点显示成 23 点）。
     */
    private static SimpleDateFormat beijingTimeFormat(String pattern) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf;
    }

    // ======================== 辅助 ========================

    private String statusLabel(ITestResult r) {
        switch (r.getStatus()) {
            case ITestResult.SUCCESS: return "PASS";
            case ITestResult.FAILURE: return "FAIL";
            case ITestResult.SKIP:    return "SKIP";
            default: return "UNKNOWN";
        }
    }

    private String descOrDash(ITestResult r) {
        String desc = r.getMethod().getDescription();
        return (desc != null && !desc.isEmpty()) ? desc : "-";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ======================== 内部 DTO ========================

    static class Stats {
        int total, passed, failed, skipped;
        Date startDate, endDate;
        List<FailDetail> failures = new ArrayList<>();
        List<ITestResult> allResults = new ArrayList<>();
    }

    static class FailDetail {
        final String className, methodName, description, exceptionMsg;
        FailDetail(ITestResult r) {
            this.className = r.getTestClass().getName();
            this.methodName = r.getMethod().getMethodName();
            this.description = r.getMethod().getDescription();
            Throwable t = r.getThrowable();
            this.exceptionMsg = t != null ? t.getClass().getSimpleName() + ": " + t.getMessage() : "-";
        }
    }
}
