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
 * Updates Node.js and NPM in the Docker container to the requested versions
 * Input:
 *  String nodeJsVersion: The desired version of Node.js (defaults to latest LTS)
 *  String npmVersion: The desired version of NPM (optional)
 */
def call(String nodeJsVersion = '--lts', String npmVersion = '') {
    echo "Updating Node to '${nodeJsVersion == "--lts" ? "lts" : nodeJsVersion}'"
    def NVM_DIR = "/home/jenkins/.nvm"
    if (sh(returnStatus: true, script: "test -f ${NVM_DIR}/nvm.sh") == 0) {
        // https://stackoverflow.com/questions/25899912/how-to-install-nvm-in-docker
        sh "source ${NVM_DIR}/nvm.sh && nvm alias default ${nvm_default()}"
        sh ". ${NVM_DIR}/nvm.sh && nvm install ${nodeJsVersion} && nvm use ${nodeJsVersion} && node -v > .nvmrc"
        nodeJsVersion = readFile(file: ".nvmrc").trim()
        env.NODE_PATH = "${NVM_DIR}/versions/node/${nodeJsVersion}/lib/node_modules"
        env.PATH = "${NVM_DIR}/versions/node/${nodeJsVersion}/bin:${env.PATH}"
    } else if (sh(returnStatus: true, script: "which n") == 0) {
        sh "n install ${nodeJsVersion == "--lts" ? "lts" : nodeJsVersion}'"
    } else {
        error "Node.js version managers (NVM, N) are not available or not properly configured. Please ensure either NVM or N are configured in your Docker container."
    }

    if (npmVersion != '') {
        echo "Updating NPM to '${npmVersion}'"
        sh "npm install -g npm@${npmVersion}"
    }
}

return this
