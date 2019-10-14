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

class DependencyDefinition  {
    /**
     * Package name of the specified dependency
     * 
     * @Note Remember to use the pull package name which can include a scope
     */
    String name

    /**
     * Package version or tag of the specified depencency
     */
    String version

    /**
     * Registry URL to which the specified dependecy should be associated
     *
     * <p>If the registry is not specified, it will default to the public NPM registry</p>
     *
     * @default "https://registry.npmjs.org/"
     */
    String registry = "https://registry.npmjs.org/"
}

