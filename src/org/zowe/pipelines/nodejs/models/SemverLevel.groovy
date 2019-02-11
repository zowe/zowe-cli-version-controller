/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs.models

/**
 * The max semver level that the package can move.
 */
enum SemverLevel {
    /**
     * In x.y.z, allows x, y, and z to increment
     */
    MAJOR,

    /**
     * In x.y.z, allows only y and z to increment
     */
    MINOR,

    /**
     * In x.y.z, allows only z to increment
     */
    PATCH
}