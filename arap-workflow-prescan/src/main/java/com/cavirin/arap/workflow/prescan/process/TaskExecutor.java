package com.cavirin.arap.workflow.prescan.process;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskExecutor {

    private static final Logger logger = LoggerFactory.getLogger(TaskExecutor.class);

    private static ExecutorService executor;

    private TaskExecutor(){
        initalize();
    }

    private static void initalize(){
        String numThreads = System.getProperty("threads", "50");
        int nthreads = Integer.parseInt(numThreads);
        executor = Executors.newFixedThreadPool(nthreads);
        // Number of threads (configured using -Dthreads=nnn
        logger.debug("Initialized thread pool with {} threads", nthreads);
    }

    public static ExecutorService getInstance(){
        if (null == executor) {
        	synchronized(TaskExecutor.class) {
        		if (null == executor) {
        			initalize();
        		}
        	}
        }
        return executor;
    }

}
