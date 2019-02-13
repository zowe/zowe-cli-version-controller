/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.arguments

import org.zowe.pipelines.base.models.StageTimeout

import java.util.concurrent.TimeUnit

/**
 * Arguments available to the
 * {@link org.zowe.pipelines.base.Pipeline#setupBase(org.zowe.pipelines.base.arguments.SetupArguments)}
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
