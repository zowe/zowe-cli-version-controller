/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic.models

import org.zowe.pipelines.base.enums.StageStatus
import org.zowe.pipelines.base.models.PipelineControl

class GenericPipelineControl extends PipelineControl {
    StageStatus build = StageStatus.ABSENT
    StageStatus test = StageStatus.ABSENT
    StageStatus version = StageStatus.ABSENT
    StageStatus deploy = StageStatus.ABSENT
}
