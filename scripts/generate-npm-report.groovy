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
 * List of people who will get all emails for master builds
 */
def MASTER_RECIPIENTS_LIST = "fernando.rijocedeno@broadcom.com"

/**
 * Options for the pipeline
 */
def opts = []
opts.push(
  parameters([
    string(name: 'ORG', defaultValue: 'zowe', description: 'Organization to gather packages from'),
    string(name: 'EXTRA_REPOS', defaultValue: 'vscode-extension-for-zowe', description: 'Add additional repositories under Zowe on Public GitHub, (comma-separated)'),
    string(name: 'RECIPIENTS_LIST', defaultValue: '', description: 'List of emails to recevie build resutls (Override)')
  ])
)
opts.push(buildDiscarder(logRotator(numToKeepStr: '10')))
opts.push(disableConcurrentBuilds())
properties(opts)

/**
 * Run the given script and process the Output as JSON
 */
def expectJSON(shellScript) {
  def _temp = sh returnStdout: true, script: shellScript
  def temp = readJSON text: _temp
  if (!temp) throw "Unexpected JSON format: ${_temp}"
  return temp
}

def generateReport(name, isRepo = false) {
  return {
    stage("Generate report: ${name}") {
      def (pkgJson, tempDir, deps, devDeps) = ["", ""]
      if (isRepo) {
        tempDir = "${name}"
        pkgJson = expectJSON("curl https://raw.githubusercontent.com/zowe/${name}/master/package.json")
      } else {
        tempDir = "${name.split('/')[1]}"
        pkgJson = expectJSON("npm view ${name} --json")
      }
      deps = pkgJson.dependencies
      devDeps = pkgJson.devDependencies
      sh "rm -rf ${tempDir} || exit 0"
      sh "mkdir ${tempDir}"
      dir("${tempDir}") {
        echo "Inside: ${tempDir}"
        sh "mkdir dev prod"
        dir("dev"){
          pkgJson.dependencies = [:]
          pkgJson.devDependencies = devDeps
          writeJSON json: pkgJson, file: "package.json"
          sh "npm install --package-lock-only"
          sh "npm audit --json > ../${tempDir}.dev.json"
        }
        dir("prod"){
          pkgJson.dependencies = deps
          pkgJson.devDependencies = [:]
          writeJSON json: pkgJson, file: "package.json"
          sh "npm install --package-lock-only"
          sh "npm audit --json > ../${tempDir}.prod.json"
        }
        sh "ls -al"
        sh "cat *.json"
      }
    }
  }
}

node('ca-jenkins-agent') {
  try {
    def buildStages = [:]
    def orgPkgs = expectJSON("npm access ls-packages @${params.ORG}")
    orgPkgs.each{ pkgName, perm ->
      buildStages.put(pkgName, generateReport(pkgName))
    }
    params.EXTRA_REPOS.split(',').each{ repoName ->
      buildStages.put(repoName, generateReport(repoName, true))
    }
    parallel(buildStages)
  } catch (e) {
    currentBuild.result = "FAILURE"
    throw e
  } finally {
    def recipients = params.RECIPIENTS_LIST != '' ? "${params.RECIPIENTS_LIST},fernando.rijocedeno@broadcom.com" : MASTER_RECIPIENTS_LIST
    emailext(
      to: recipients,
      subject: "[${currentBuild.currentResult}] Reports generated. ORG: ${params.ORG}",
      body: """
      <p>Build result: ${currentBuild.absoluteUrl}</p>
      """
    );
  }
}
