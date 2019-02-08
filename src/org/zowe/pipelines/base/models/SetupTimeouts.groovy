package org.zowe.pipelines.base.models

import java.util.concurrent.TimeUnit

class SetupTimeouts {
    StageTimeout setup = [time: 10, unit: TimeUnit.SECONDS]
    StageTimeout checkout = [time: 1, unit: TimeUnit.MINUTES]
}
