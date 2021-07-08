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

import org.zowe.pipelines.generic.arguments.VersionStageArguments

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.nodejs.NodeJSPipeline#version(java.util.Map)} method.
 */
class NodeJSVersionStageArguments extends VersionStageArguments {
    /**
     * If any arguments are defined, the update changelog stage will be invoked
     * with them.
     */
    Map<String, String> updateChangelogArgs

    /**
     * If specified, the version stage will run in this project subdirectory.
     */
    String inDir
}
