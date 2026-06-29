package org.example.base.retry;


import org.testng.IAnnotationTransformer;
import org.testng.IRetryAnalyzer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 全局注入转换器
 *
 * @author Kiko Song
 * @create 2026-06-09 12:00
 */
public class GlobalRetryTransformer implements IAnnotationTransformer {
    @Override
    public void transform(ITestAnnotation annotation, Class testClass,
                          Constructor testConstructor, Method testMethod) {
        System.out.println("====== [DEBUG] Transformer triggered for: " +
                (testMethod != null ? testMethod.getName() : "class-level"));
        Class<? extends IRetryAnalyzer> existingAnalyzer = annotation.getRetryAnalyzerClass();

        boolean isDefaultOrEmpty = (existingAnalyzer == null)
                || existingAnalyzer.getName().equals("org.testng.IRetryAnalyzer")
                || existingAnalyzer.getName().contains("NoOp"); // 兼容不同版本的默认实现

        if (!isDefaultOrEmpty) {
            System.out.println("[DEBUG] Skip global retry for " + testMethod.getName() + ", using custom: " + existingAnalyzer);
            return;
        }

//        // 2. 排除标记了 @NoRetry 的方法
//        if (testMethod != null && testMethod.isAnnotationPresent(NoRetry.class)) {
//            return;
//        }

//        // 3. 排除特定分组（如冒烟测试要求快速反馈，不重试）
//        String[] groups = annotation.getGroups();
//        if (groups != null && Arrays.asList(groups).contains("smoke")) {
//            return;
//        }

        // 4. 全局注入
        annotation.setRetryAnalyzer(SmartRetryAnalyzer.class);
        // ✅ 加这一行确认绑定成功
        System.out.println("====== [DEBUG] RetryAnalyzer bound: " +
                annotation.getRetryAnalyzerClass());
    }
}
