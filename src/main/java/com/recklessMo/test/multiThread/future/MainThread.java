package com.recklessMo.test.multiThread.future;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 * 用于汇总结果
 *
 * Created by hpf on 10/12/17.
 */
public class MainThread {


    /**
     * 这段代码的结果?
     *
     * 用法不对,但是结果很有意思
     *
     * 返回的executorservice被包装了一层, 而且外层对象在不可达之后在finalize里面调用了线程池的shutdown方法
     * 所以就导致线程池关闭
     *
     * @throws Exception
     */
    public void start() throws Exception{
        List<ExecutorService> result = new LinkedList<>();
        for (int i = 0; i < 50; i++) {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            result.add(executorService);
            executorService.execute(() -> System.out.println("test"));
        }
    }

    public static void main(String[] args) throws  Exception{
        new MainThread().start();
        System.gc();
    }
}
