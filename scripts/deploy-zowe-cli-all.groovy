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
 * Constants to be used throughout the pipeline
 */
def CONST = [
  cdJobName: 'zowe-cli-deploy-component',
  // Packages to be deployed
  packages: ['imperative', 'perf-timing', 'cli', 'cics', 'db2'],
  // Tags on each package to be deployed
  tags: ['daily', 'latest']
]

import java.text.SimpleDateFormat;

node('ca-jenkins-agent') {
  try {
    checkout scm
    stage ('Submit Jobs') {
      CONST.packages.each{ pkgName ->
        CONST.tags.each { tagName ->
          build job: CONST.cdJobName, parameters: [
            [$class: 'StringParameterValue', name: 'PKG_NAME', value: pkgName],
            [$class: 'StringParameterValue', name: 'PKT_TAG', value: tagName]
          ]
        }
      }
    }
  } catch (e) {
    error "${e.getMessage()}"
  } finally {
    def buildStatus = currentBuild.currentResult
    def recipients = "${MASTER_RECIPIENTS_LIST}"
    def details = "Submission of CD jobs"
    try {
      if (buildStatus.equals("SUCCESS")) {
        details = "${details} succeded."
      } else {
        details = "${details} failed.\n\nPlease investigate build ${currentBuild.number}"
      }
      details = "${details}\n\nBuild result: ${currentBuild.absoluteUrl}"
      emailext(to: recipients, subject: "[${buildStatus}] CD jobs submission", body: details)
    } catch (e) {
      echo "Experienced an error sending an email for a ${buildStatus} build"
      currentBuild.result = buildStatus
      echo "${details}"
    }
  }
}
