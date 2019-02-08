package org.zowe.pipelines.generic.models

import org.zowe.pipelines.base.models.SetupTimeouts
import org.zowe.pipelines.base.models.StageTimeout

import java.util.concurrent.TimeUnit

class GenericSetupTimeouts extends SetupTimeouts {
    StageTimeout gitSetup = [time: 1, unit: TimeUnit.MINUTES]
    StageTimeout ciSkip = [time: 1, unit: TimeUnit.MINUTES]

}
