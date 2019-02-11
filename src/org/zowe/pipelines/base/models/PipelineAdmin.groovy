/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.base.models

final class PipelineAdmin {
    final String userID
    final String email
    final String name

    PipelineAdmin(String userId, String email, String name) {
        this.userID = userId
        this.email = email
        this.name = name
    }
}
