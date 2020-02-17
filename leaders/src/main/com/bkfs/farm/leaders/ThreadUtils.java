package com.bkfs.farm.leaders;

import java.util.concurrent.ThreadFactory;

import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class ThreadUtils
{
    

	
    public static void checkInterrupted(Throwable e)
    {
        if ( e instanceof InterruptedException )
        {
            Thread.currentThread().interrupt();
        }
    }
    
    
    public static ThreadFactory newThreadFactory(String processName)
    {
        return newGenericThreadFactory("BKFS-" + processName);
    }
    

    
    public static ThreadFactory newGenericThreadFactory(String processName)
    {
        Thread.UncaughtExceptionHandler uncaughtExceptionHandler = new Thread.UncaughtExceptionHandler()
        {
            @Override
            public void uncaughtException(Thread t, Throwable e)
            {
                //log.error("Unexpected exception in thread: " + t, e);
                Throwables.propagate(e);
            }
        };
        
        return new ThreadFactoryBuilder()
            .setNameFormat(processName + "-%d")
            .setDaemon(true)
            .setUncaughtExceptionHandler(uncaughtExceptionHandler)
            .build();
    }
    
    

    public static String getProcessName(Class<?> clazz)
    {
        if ( clazz.isAnonymousClass() )
        {
            return getProcessName(clazz.getEnclosingClass());
        }
        return clazz.getSimpleName();
    }
}
