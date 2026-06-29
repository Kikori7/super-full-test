package org.example.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * @author Kiko Song
 * @create 2026-06-12 9:18
 */
public class BigDecimalDemo {
    public static void main(String[] args) {
        BigDecimal a = new BigDecimal("10.0");
        BigDecimal b = new BigDecimal("3.0");

        // 1. 加法 (Add)
        BigDecimal sum = a.add(b);

        // 2. 减法 (subtract)
        BigDecimal diff = a.subtract(b);


        // 3. 执行除法并保留 10 位小数
        // 参数说明：
        // scale: 保留的小数位数 (10)
        // roundingMode: 舍入模式 (通常用 HALF_UP，即四舍五入)
        BigDecimal result = a.divide(b, 10, RoundingMode.HALF_UP);

        System.out.println("结果: " + result);
        // 输出: 3.3333333333

        // 4.乘法同样适用，如果需要限制结果位数
        BigDecimal mulResult = a.multiply(b).setScale(10, RoundingMode.HALF_UP);
        BigDecimal.valueOf(0.1);
    }
}
