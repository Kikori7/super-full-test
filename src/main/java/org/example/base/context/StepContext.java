package org.example.base.context;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试步骤上下文 — 基于 ThreadLocal 的轻量级执行跟踪.
 *
 * <h3>设计目的</h3>
 * 正常执行过程中持续记录"当前在做什么"，异常发生时直接读取，
 * 不用再去页面上抓（那时页面可能已经变了或崩了）。
 *
 * <h3>线程模型</h3>
 * 每个测试线程拥有独立副本，TestNG 并发执行时互不干扰。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // BaseTest @BeforeMethod
 * StepContext.init(testMethodName);
 * StepContext.put("merchantId", "5");
 *
 * // BasePage 每个操作后
 * StepContext.record("点击 [iPhone 15] 加入购物车");
 *
 * // 异常处理时
 * FailureSnapshot snapshot = StepContext.snapshot(page);
 *
 * // BaseTest @AfterMethod
 * StepContext.clear();
 * }</pre>
 *
 * @author Kiko Song
 * @since 2026-06-27
 */
public final class StepContext {

    // ======================== ThreadLocal 字段 ========================

    /** 步骤调用链（栈结构，方便看调用层级） */
    private static final ThreadLocal<Deque<String>> STEP_STACK =
            ThreadLocal.withInitial(ArrayDeque::new);

    /** 当前测试用例名称 */
    private static final ThreadLocal<String> TEST_NAME =
            new ThreadLocal<>();

    /** 上一步操作描述 */
    private static final ThreadLocal<String> LAST_STEP =
            new ThreadLocal<>();

    /** 自定义扩展数据（测试数据、业务参数等） */
    private static final ThreadLocal<Map<String, Object>> EXTRAS =
            ThreadLocal.withInitial(HashMap::new);

    /** 进入页面时记录 */
    private static final ThreadLocal<String> ENTRY_URL =
            new ThreadLocal<>();

    // ======================== 初始化 & 清理 ========================

    /** 在新用例开始时调用（由 BaseTest @BeforeMethod 触发） */
    public static void init(String testName) {
        clear(); // 先清理上一用例残留
        TEST_NAME.set(testName);
        STEP_STACK.get().clear();
    }

    /** 在用例结束时调用（由 BaseTest @AfterMethod 触发），防止内存泄漏 */
    public static void clear() {
        STEP_STACK.get().clear();
        TEST_NAME.remove();
        LAST_STEP.remove();
        EXTRAS.get().clear();
        ENTRY_URL.remove();
    }

    // ======================== 步骤记录 ========================

    /**
     * 记录一个步骤，自动设为 lastStep 并压入调用栈.
     * 由 BasePage 的 click/fill/navigate 等方法内部调用。
     */
    public static void record(String stepDescription) {
        LAST_STEP.set(stepDescription);
        STEP_STACK.get().push(stepDescription);
    }

    /** 记录进入页面 */
    public static void recordEntry(String url) {
        ENTRY_URL.set(url);
        record("🌐 进入页面: " + url);
    }

    /** 进入一个业务层级（如：进入结算流程），在栈上添加标记 */
    public static void enterScope(String scope) {
        STEP_STACK.get().push("▶ " + scope);
    }

    /** 退出一个业务层级 */
    public static void exitScope() {
        Deque<String> stack = STEP_STACK.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    // ======================== 扩展数据 ========================

    /** 存入自定义键值（商家ID、商品名等业务上下文） */
    public static void put(String key, Object value) {
        EXTRAS.get().put(key, value);
    }

    /** 读取自定义键值 */
    public static Object get(String key) {
        return EXTRAS.get().get(key);
    }

    // ======================== 快照生成 ========================

    /**
     * 基于当前 ThreadLocal 内容生成不可变的快照对象.
     * 异常发生时调用，把这一刻的上下文冻结下来。
     *
     * @param pageUrl 当前页面 URL（由调用方传入，不直接从 page 读以兼容 page 为 null 的场景）
     */
    public static FailureSnapshot snapshot(String pageUrl, String pageTitle) {
        Deque<String> stack = STEP_STACK.get();
        List<String> chain = new ArrayList<>(stack);
        Collections.reverse(chain); // 栈是 FILO，转成时间顺序

        return new FailureSnapshot(
                TEST_NAME.get(),
                LAST_STEP.get(),
                Collections.unmodifiableList(chain),
                ENTRY_URL.get(),
                pageUrl,
                pageTitle,
                new HashMap<>(EXTRAS.get()),
                System.currentTimeMillis()
        );
    }

    // ======================== 只读查询 ========================

    public static String getTestName() { return TEST_NAME.get(); }
    public static String getLastStep() { return LAST_STEP.get(); }
    public static List<String> getStepChain() {
        List<String> chain = new ArrayList<>(STEP_STACK.get());
        Collections.reverse(chain);
        return Collections.unmodifiableList(chain);
    }

    private StepContext() {}
}
