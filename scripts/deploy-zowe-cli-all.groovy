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
def MASTER_RECIPIENTS_LIST = "andrew.harn@broadcom.com, timothy.johnson@broadcom.com, fernando.rijocedeno@broadcom.com"

def deployTags(pkgName, props) {
  if (props.packages) {
    def buildStages = [:]
    props.packages.each { subPkgName, subProps ->
      buildStages.put("@zowe/${subPkgName}", deployTags(subPkgName, subProps))
    }
    return stages(buildStages)
  } else {
    return {
      stage("Deploy: @zowe/${pkgName}") {
        props.tags.each { tagName ->
          echo "Deploy @zowe/${pkgName}@${tagName}"
          build job: 'zowe-cli-deploy-component', parameters: [
            [$class: 'StringParameterValue', name: 'PKG_NAME', value: pkgName],
            [$class: 'StringParameterValue', name: 'PKG_TAG', value: tagName]
          ]
        }
      }
    }
  }
}

node('ca-jenkins-agent') {
  try {
    checkout scm
    def constObj = readYaml file: "deploy-constants.yaml"
    def buildStages = [:]
    constObj.packages.each { pkgName, props ->
      buildStages.put("@zowe/${pkgName}", deployTags(pkgName, props))
    }
    parallel(buildStages)
  } catch (e) {
    currentBuild.result = "FAILURE"
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
