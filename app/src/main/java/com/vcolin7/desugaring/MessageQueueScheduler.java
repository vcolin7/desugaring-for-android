// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.vcolin7.desugaring;

import android.os.Handler;
import android.os.Message;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.scheduler.Scheduler;

/**
 * An implementation of Reactor {@link reactor.core.scheduler.Scheduler} that post {@link java.lang.Runnable} work
 * to Android Message queue.
 */
final class MessageQueueScheduler implements Scheduler {
    /**
     * The handler to post work to MessageQueue and to receive work from MessageQueue.
     */
    private final Handler msgQueueHandler;
    private final boolean isAsync;

    /**
     * Creates MessageQueueScheduler.
     *
     * @param msgQueueHandler a handler to the message queue
     */
    MessageQueueScheduler(Handler msgQueueHandler) {
        this.msgQueueHandler = msgQueueHandler;
        this.isAsync = false;
    }

    /**
     * Creates MessageQueueScheduler.
     *
     * @param msgQueueHandler a handler to the MessageQueue
     * @param isAsync indicates whether the message is asynchronous
     */
    MessageQueueScheduler(Handler msgQueueHandler, boolean isAsync) {
        this.msgQueueHandler = msgQueueHandler;
        this.isAsync = isAsync;
    }

    @Override
    public Disposable schedule(Runnable work) {
        Objects.requireNonNull(work, "work cannot be null");
        return this.scheduleIntern(work, null, null);
    }

    @Override
    public Disposable schedule(Runnable work, long delay, TimeUnit unit) {
        Objects.requireNonNull(work, "work cannot be null");
        Objects.requireNonNull(unit, "unit cannot be null");
        return this.scheduleIntern(work, delay, unit);
    }

    @Override
    public Worker createWorker() {
        return new MessageQueueWorker(msgQueueHandler, isAsync);
    }

    private Disposable scheduleIntern(Runnable work, Long delay, TimeUnit unit) {
        DisposableWork disposableWork = new DisposableWork(this.msgQueueHandler, work);
        Message message = Message.obtain(this.msgQueueHandler, disposableWork);
        message.setAsynchronous(this.isAsync);
        if (delay != null) {
            this.msgQueueHandler.sendMessageDelayed(message, unit.toMillis(delay));
        } else {
            this.msgQueueHandler.sendMessage(message);
        }
        return disposableWork;
    }

    /**
     * An implementation of {@code Worker} that sends {@code Runnable} work to Android MessageQueue.
     */
    private static final class MessageQueueWorker implements Worker {
        /**
         * The handler to send work to MessageQueue and to receive work from MessageQueue.
         */
        private final Handler msgQueueHandler;
        private final boolean isAsync;
        private volatile boolean disposed;

        MessageQueueWorker(Handler msgQueueHandler, boolean isAsync) {
            this.msgQueueHandler = msgQueueHandler;
            this.isAsync = isAsync;
        }

        @Override
        public Disposable schedule(Runnable work) {
            Objects.requireNonNull(work, "work cannot be null");
            return this.scheduleIntern(work, null, null);
        }

        @Override
        public Disposable schedule(Runnable work, long delay, TimeUnit unit) {
            Objects.requireNonNull(work, "work cannot be null");
            Objects.requireNonNull(unit, "unit cannot be null");
            return this.scheduleIntern(work, delay, unit);
        }

        @Override
        public void dispose() {
            this.disposed = true;
            this.msgQueueHandler.removeCallbacksAndMessages(this);
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }

        /**
         * Schedule the work to the MessageQueue.
         *
         * @param work the work to schedule
         * @param delay the delay in posting work
         * @param unit the delay unit
         * @return Disposable to dispose work
         */
        private Disposable scheduleIntern(Runnable work, Long delay, TimeUnit unit) {
            if (this.disposed) {
                return Disposables.disposed();
            }
            DisposableWork disposableWork = new DisposableWork(this.msgQueueHandler, work);
            // Get a message configured:
            //  [1]. to be eventually received by this.msgQueueHandler for handing.
            //  [2]. with a disposableWork (Runnable) to be invoked upon handling.
            Message message = Message.obtain(this.msgQueueHandler, disposableWork);
            // Set this worker reference as token so that MessageQueueWorker::dispose()
            // can identify it as a message holding one of the work scheduled by this worker
            // and can remove along with other such works.
            message.obj = this;
            message.setAsynchronous(this.isAsync);
            // Post the message to MessageQueue (e.g. UIThread MessageQueue) via it's handler.
            if (delay != null) {
                this.msgQueueHandler.sendMessageDelayed(message, unit.toMillis(delay));
            } else {
                this.msgQueueHandler.sendMessage(message);
            }
            if (this.disposed) {
                // Upon worker disposal remove the work from the MessageQueue if it's still pending.
                this.msgQueueHandler.removeCallbacks(disposableWork);
                return Disposables.disposed();
            }
            return disposableWork;
        }
    }

    /**
     * A work that is send to the MessageQueue via {@link android.os.Handler} and received by the same handler
     * from MessageQueue. The handler invoke run() method of work. Work can be disposed if it is not yet picked
     * by the handler.
     */
    private static final class DisposableWork implements Runnable, Disposable {
        private final Handler msgQueueHandler;
        private final Runnable work;
        private volatile boolean disposed;

        DisposableWork(Handler msgQueueHandler, Runnable work) {
            this.msgQueueHandler = msgQueueHandler;
            this.work = work;
        }

        @Override
        public void run() {
            try {
                this.work.run();
            } catch (Throwable t) {
                AndroidScheduler.handleError(t);
            }
        }

        @Override
        public void dispose() {
            this.msgQueueHandler.removeCallbacks(this);
            this.disposed = true;
        }

        @Override
        public boolean isDisposed() {
            return this.disposed;
        }
    }
}
