package org.zowe.pipelines.generic.arguments

import org.zowe.pipelines.base.arguments.SetupArguments
import org.zowe.pipelines.base.models.StageTimeout

import java.util.concurrent.TimeUnit

class GenericSetupArguments extends SetupArguments {
    StageTimeout gitSetup = [time: 1, unit: TimeUnit.MINUTES]
    StageTimeout ciSkip = [time: 1, unit: TimeUnit.MINUTES]

}
