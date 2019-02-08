package org.zowe.pipelines.nodejs.models

import org.zowe.pipelines.base.models.StageTimeout
import org.zowe.pipelines.generic.models.GenericSetupTimeouts

import java.util.concurrent.TimeUnit

class NodeJSSetupTimeouts extends GenericSetupTimeouts {
    StageTimeout installDependencies = [time: 5, unit: TimeUnit.MINUTES]
}
