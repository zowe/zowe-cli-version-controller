package org.zowe.pipelines.base.models

import groovy.transform.AutoClone

import static groovy.transform.AutoCloneStyle.SIMPLE

/**
 * Stage timeout information.
 */
@AutoClone(style = SIMPLE)
class StageTimeout {
    /**
     * The amount of time a stage is allowed to execute.
     *
     * <p><b>Default:</b> {@code 10}</p>
     */
    int time = 10

    /**
     * The unit of measurement for {@link #time}
     *
     * <p><b>Default:</b> {@code "MINUTES"}</p>
     */
    String unit = 'MINUTES'
}
