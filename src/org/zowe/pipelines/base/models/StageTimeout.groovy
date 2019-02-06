package org.zowe.pipelines.base.models

import java.util.concurrent.TimeUnit

/**
 * Stage timeout information.
 */
class StageTimeout {
    /**
     * The amount of time a stage is allowed to execute.
     *
     * <p><b>Default:</b> {@code 10}</p>
     */
    long time = 10

    /**
     * The unit of measurement for {@link #time}
     *
     * <p><b>Default:</b> {@link TimeoutUnit#MINUTES}</p>
     */
    TimeUnit unit = TimeUnit.MINUTES

    String toString() {
        return "${time} ${unit}"
    }
}
