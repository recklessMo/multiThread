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


    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    /**
     * @throws Exception
     */
    public void start() throws Exception{
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(()->System.out.println("异步执行!"));
    }

    public static void main(String[] args) throws  Exception{
        new MainThread().start();
    }
}
