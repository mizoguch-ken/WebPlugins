package ken.mizoguch.webviewer.llm;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative and asynchronous cancellation token capable of aborting pending HTTP futures,
 * closing blocked response streams, and waking retry backoff sleeps immediately. Also provides
 * {@link #abortStream()} so watchdogs can close an idle socket without marking user cancellation.
 *
 * @author mizoguch-ken
 */
final class CancellationToken {

    static final CancellationToken NONE = new CancellationToken(false);

    private final boolean cancellable_;
    private final AtomicBoolean cancelled_ = new AtomicBoolean(false);
    private final CountDownLatch latch_ = new CountDownLatch(1);
    private CompletableFuture<?> future_;
    private AutoCloseable stream_;

    CancellationToken() {
        this(true);
    }

    private CancellationToken(boolean cancellable) {
        cancellable_ = cancellable;
    }

    boolean isCancelled() {
        return cancelled_.get();
    }

    void cancel() {
        if (!cancellable_ || !cancelled_.compareAndSet(false, true)) {
            return;
        }
        latch_.countDown();
        CompletableFuture<?> future;
        AutoCloseable stream;
        synchronized (this) {
            future = future_;
            stream = stream_;
            future_ = null;
            stream_ = null;
        }
        if (future != null) {
            future.cancel(true);
        }
        quietlyClose(stream);
    }

    /**
     * Closes only the bound stream without setting {@link #isCancelled()}, allowing watchdogs
     * to unblock a stalled read while reporting a timeout rather than user cancellation.
     */
    void abortStream() {
        AutoCloseable stream;
        synchronized (this) {
            stream = stream_;
            stream_ = null;
        }
        quietlyClose(stream);
    }

    void bindFuture(CompletableFuture<?> future) {
        if ((future == null) || !cancellable_) {
            return;
        }
        boolean cancelNow;
        synchronized (this) {
            cancelNow = cancelled_.get();
            if (!cancelNow) {
                future_ = future;
            }
        }
        if (cancelNow) {
            future.cancel(true);
        }
    }

    void unbindFuture(CompletableFuture<?> future) {
        if (!cancellable_) {
            return;
        }
        synchronized (this) {
            if (future_ == future) {
                future_ = null;
            }
        }
    }

    void bindStream(AutoCloseable stream) {
        if ((stream == null) || !cancellable_) {
            return;
        }
        boolean closeNow;
        synchronized (this) {
            closeNow = cancelled_.get();
            if (!closeNow) {
                stream_ = stream;
            }
        }
        if (closeNow) {
            quietlyClose(stream);
        }
    }

    void unbindStream(AutoCloseable stream) {
        if (!cancellable_) {
            return;
        }
        synchronized (this) {
            if (stream_ == stream) {
                stream_ = null;
            }
        }
    }

    boolean awaitMillis(long millis) {
        if (cancelled_.get()) {
            return false;
        }
        if (millis <= 0L) {
            return true;
        }
        try {
            boolean fired = latch_.await(millis, TimeUnit.MILLISECONDS);
            return !fired && !cancelled_.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void quietlyClose(AutoCloseable resource) {
        if (resource == null) {
            return;
        }
        try {
            resource.close();
        } catch (Exception ignored) {
            // Best-effort stream teardown.
        }
    }
}
