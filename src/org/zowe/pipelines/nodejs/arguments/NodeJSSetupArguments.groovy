package org.zowe.pipelines.nodejs.arguments

import org.zowe.pipelines.base.models.StageTimeout
import org.zowe.pipelines.generic.args.GenericSetupArgs
import org.zowe.pipelines.generic.arguments.GenericSetupArguments

import java.util.concurrent.TimeUnit

class NodeJSSetupArguments extends GenericSetupArguments {
    StageTimeout installDependencies = [time: 5, unit: TimeUnit.MINUTES]
}
