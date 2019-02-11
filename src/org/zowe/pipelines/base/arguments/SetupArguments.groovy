package org.zowe.pipelines.base.arguments

import org.zowe.pipelines.base.models.StageTimeout

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the {@link org.zowe.pipelines.base.Pipeline#setupBase(SetupArguments)}
 * method.
 */
class SetupArguments {
    /**
     * Amount of time allowed for the pipeline setup.
     *
     * @default 10 Seconds
     */
    StageTimeout setup = [time: 10, unit: TimeUnit.SECONDS]

    /**
     * Amount of time allowed for source code checkout.
     *
     * @default 1 Minute
     */
    StageTimeout checkout = [time: 1, unit: TimeUnit.MINUTES]
}
