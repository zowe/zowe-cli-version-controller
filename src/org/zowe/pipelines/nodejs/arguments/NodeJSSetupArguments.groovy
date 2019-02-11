/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs.arguments

import org.zowe.pipelines.base.models.StageTimeout
import org.zowe.pipelines.generic.arguments.GenericSetupArguments

import java.util.concurrent.TimeUnit

class NodeJSSetupArguments extends GenericSetupArguments {
    StageTimeout installDependencies = [time: 5, unit: TimeUnit.MINUTES]
}
