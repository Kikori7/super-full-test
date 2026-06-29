package org.example;

import org.example.base.retry.SmartRetryAnalyzer;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * @author Kiko Song
 * @create 2026-06-09 14:52
 */
public class DemoTest {
    private int counter = 0;
//    @Test(retryAnalyzer = SmartRetryAnalyzer.class)
//    public void fun(){
//        counter++;
//        System.out.println("执行次数: " + counter);
//        Assert.assertEquals(counter, 3, "前两次故意失败，第三次应该成功");
//    }
    @Test
    public void fun2(){
        counter++;
        System.out.println("执行次数: " + counter);
        Assert.assertEquals(counter, 3, "前两次故意失败，第三次应该成功");
    }
}
