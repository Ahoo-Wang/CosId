package me.ahoo.cosid.spring.boot.starter.segment;

import lombok.extern.slf4j.Slf4j;
import me.ahoo.cosid.provider.IdGeneratorProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.SmartLifecycle;

/**
 * @author ahoo wang
 */
@Slf4j
public class LifecycleSegmentChainId implements SmartLifecycle {
    private volatile boolean running;
    private final IdGeneratorProvider idGeneratorProvider;

    public LifecycleSegmentChainId(IdGeneratorProvider idGeneratorProvider) {
        this.idGeneratorProvider = idGeneratorProvider;
    }

    /**
     * Start this component.
     * <p>Should not throw an exception if the component is already running.
     * <p>In the case of a container, this will propagate the start signal to all
     * components that apply.
     *
     * @see SmartLifecycle#isAutoStartup()
     */
    @Override
    public void start() {
        running = true;
    }

    /**
     * Stop this component, typically in a synchronous fashion, such that the component is
     * fully stopped upon return of this method. Consider implementing {@link SmartLifecycle}
     * and its {@code stop(Runnable)} variant when asynchronous stop behavior is necessary.
     * <p>Note that this stop notification is not guaranteed to come before destruction:
     * On regular shutdown, {@code Lifecycle} beans will first receive a stop notification
     * before the general destruction callbacks are being propagated; however, on hot
     * refresh during a context's lifetime or on aborted refresh attempts, a given bean's
     * destroy method will be called without any consideration of stop signals upfront.
     * <p>Should not throw an exception if the component is not running (not started yet).
     * <p>In the case of a container, this will propagate the stop signal to all components
     * that apply.
     *
     * @see SmartLifecycle#stop(Runnable)
     * @see DisposableBean#destroy()
     */
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        idGeneratorProvider.getAll().stream().filter(idGenerator -> idGenerator instanceof AutoCloseable).map(idGenerator -> (AutoCloseable) idGenerator).forEach(autoCloseable -> {
            try {
                autoCloseable.close();
            } catch (Exception exception) {
                if (log.isErrorEnabled()) {
                    log.error(exception.getMessage(), exception);
                }
            }
        });
    }

    /**
     * Check whether this component is currently running.
     * <p>In the case of a container, this will return {@code true} only if <i>all</i>
     * components that apply are currently running.
     *
     * @return whether the component is currently running
     */
    @Override
    public boolean isRunning() {
        return running;
    }
}
