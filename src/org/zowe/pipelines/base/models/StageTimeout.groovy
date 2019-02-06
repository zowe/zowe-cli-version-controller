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
     * <p><b>Default:</b> {@link java.util.concurrent.TimeUnit#MINUTES}</p>
     */
    TimeUnit unit = TimeUnit.MINUTES

    String toString() {
        return "${time} ${unit}"
    }

    /**
     * Add a timeout to a timeout
     * @param value The timeout to add
     * @return A new timeout object representing the operation. The returned value's
     *         unit will be that of the input time.
     */
    StageTimeout add(StageTimeout value) {
        return new StageTimeout(
                unit: value.unit,
                time: unit.convert(time, value.unit) + time
        )
    }

    /**
     * Add using a map of a timeout.
     *
     * @param value The StageTimeout map to construct
     * @return A new timeout object representing the operation
     * @see #add(StageTimeout)
     */
    StageTimeout add(Map value) {
        return add(new StageTimeout(value))
    }

    /**
     * Subtracts a timeout from a timeout
     * @param value The timeout to subtract
     * @return A new timeout object representing the operation. The returned value's
     *         unit will be that of the input StageTimeout.
     */
    StageTimeout subtract(StageTimeout value) {
        return new StageTimeout(
                unit: value.unit,
                time: unit.convert(time, value.unit) - time
        )
    }

    /**
     * Add using a map of a timeout.
     *
     * @param value The StageTimeout map to construct
     * @return A new timeout object representing the operation
     * @see #subtract(StageTimeout)
     */
    StageTimeout subtract(Map value) {
        return subtract(new StageTimeout(value))
    }
}
