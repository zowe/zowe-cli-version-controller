/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

/**
 * Performs an npm view and returns information about the given package or throws an error
 * Input: 
 *  String pkg:  The full name of the package to retrieve the information from (@scope/pkgName@tag)
 *  String opts: The options to be provided to the npm commands (for authentication purposes)
 *  String prop: The field to retrieve from the 'npm view command'
 */
def call(String pkg, String opts = '', String prop = 'version') {
    echo "Getting '${prop}' for package: ${pkg}"
    def ret = sh returnStatus: true, script: "npm view ${pkg} ${opts}"
    def val = ""
    if (ret == 0) {
        val = sh returnStdout: true, script: "npm view ${pkg} ${prop} ${opts}"
        return val.trim()
    } else {
        throw new Exception("Package not found")
    }
}

return this