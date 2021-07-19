/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

def NVM_DIR = "/home/jenkins/.nvm"

/**
 * Updates Node.js and NPM in the Docker container to the requested versions
 * Input: 
 *  String nodeJsVersion: The desired version of Node.js (defaults to latest LTS)
 *  String npmVersion: The desired version of NPM (optional)
 */
def call(String nodeJsVersion = '--lts', String npmVersion = '') {
    // https://stackoverflow.com/questions/25899912/how-to-install-nvm-in-docker
    echo "Updating Node to '${nodeJsVersion == "--lts" ? "lts" : nodeJsVersion}'"
    sh ". ${NVM_DIR}/nvm.sh && nvm install ${nodeJsVersion} && nvm use ${nodeJsVersion} && node -v > .nvmrc"
    nodeJsVersion = readFile(file: ".nvmrc").trim()
    env.NODE_PATH = "${NVM_DIR}/versions/node/${nodeJsVersion}/lib/node_modules"
    env.PATH = "${NVM_DIR}/versions/node/${nodeJsVersion}/bin:${env.PATH}"

    if (npmVersion != '') {
        echo "Updating NPM to '${npmVersion}'"
        sh "npm install -g npm@${npmVersion}"
    }
}

return this
