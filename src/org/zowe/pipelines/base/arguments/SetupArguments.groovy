package org.zowe.pipelines.base.arguments

import org.zowe.pipelines.base.models.StageTimeout

import java.util.concurrent.TimeUnit

class SetupArguments {
    StageTimeout setup = [time: 10, unit: TimeUnit.SECONDS]
    StageTimeout checkout = [time: 1, unit: TimeUnit.MINUTES]
}
