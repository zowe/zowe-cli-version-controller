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
  if (!temp) throw "Unexpected JSON format: ${temp}"
  return temp
}

def generateReport(pkgName) {
  return {
    stage("Generate report: ${pkgName}") {
      def tempDir = "temp-${pkgName.split('/')[1]}"
      def repoInfo = expectJSON("npm view ${pkgName} repository")
      sh "rm -rf ${tempDir} || exit 0"
      sh "git clone ${repoInfo.url} ${tempDir}"
      dir("${tempDir}") {
        echo "Inside: ${tempDir}"
        sh "ls -al"
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
