/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.nodejs.enums

/**
 * The options available to filter output of "lerna list" command.
 */
enum LernaFilter {
    /**
     * List all packages.
     */
    ALL,

    /**
     * List only packages that have changed since the last Git tag.
     */
    CHANGED,

    /**
     * List only packages that have changed in this PR.
     * Not supported in branch builds.
     */
    CHANGED_IN_PR
}
