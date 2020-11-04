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

/**
 * Represents the arguments available to the
 * {@link org.zowe.pipelines.nodejs.NodeJSPipeline#version(java.util.Map)} method.
 */
class NodeJSVersionStageArguments extends VersionStageArguments {
    /**
     * If any arguments are defined, the update changelog stage will be invoked
     * with them.
     *
     * @default {@code [:]}
     */
    Map<String, String> updateChangelogArgs = [:]
}
