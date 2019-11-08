// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.vcolin7.desugaring;

import android.os.Handler;
import android.os.Looper;

import java.util.function.BiConsumer;

import reactor.core.Exceptions;
import reactor.core.scheduler.Scheduler;

/**
 * Factory type to get implementation of {@link reactor.core.scheduler.Scheduler} for Android,
 * such a Scheduler is backed by Android MessageQueue.
 */
public class AndroidScheduler {
    // Handler to receive the errors that cannot be send to reactor type onError handler
    private static volatile BiConsumer<Thread, ? super Throwable> errorHandler;

    private static final class MainThreadMessageQueueSchedulerHolder {
        // Putting this field in an inner class makes it so it is only instantiated when
        // mainThread() is called instead of instantiating when other members are accessed.
        static final MessageQueueScheduler MAIN_THREAD_MESSAGE_QUEUE_SCHEDULER = new MessageQueueScheduler(new Handler(Looper.getMainLooper()));
    }

    private AndroidScheduler() {}

    /**
     * Get a {@link Scheduler} which executes work on the AndroidMainThread (UI Thread).
     *
     * @return the scheduler
     */
    public static Scheduler mainThread() {
        return MainThreadMessageQueueSchedulerHolder.MAIN_THREAD_MESSAGE_QUEUE_SCHEDULER;
    }

    /**
     * Get a Reactor Scheduler that executes work on {@code looper}.
     *
     * @param looper the looper
     * @param async should the message posting to the looper be async
     * @return the Reactor {@link Scheduler}
     */
    public static Scheduler fromLooper(Looper looper, boolean async) {
        return new MessageQueueScheduler(new Handler(looper), async);
    }

    /**
     * Sets error handler for undeliverable exceptions.
     *
     * @param handler undeliverable exception handler
     */
    public static void setErrorHandler(BiConsumer<Thread, ? super Throwable> handler) {
        errorHandler = handler;
    }

    /**
     * Package private method to handle undeliverable exceptions.
     *
     * @param error the exception to handle
     */
    static void handleError(Throwable error) {
        BiConsumer<Thread, ? super Throwable> eHandler = errorHandler;
        if (eHandler != null) {
            try {
                eHandler.accept(Thread.currentThread(), error);
                return;
            } catch (Throwable e) {
                e.printStackTrace();
                reportUncaughtException(e);
            }
        }
        error.printStackTrace();
        reportUncaughtException(error);
    }

    /**
     * Report exception to current thread via
     * {@link java.lang.Thread.UncaughtExceptionHandler#uncaughtException(Thread, Throwable)}.
     *
     * @param error the exception
     */
    private static void reportUncaughtException(Throwable error) {
        Thread currentThread = Thread.currentThread();
        Thread.UncaughtExceptionHandler handler = currentThread.getUncaughtExceptionHandler();
        if (handler != null) {
            handler.uncaughtException(currentThread, Exceptions.unwrap(error));
        }
    }
}
