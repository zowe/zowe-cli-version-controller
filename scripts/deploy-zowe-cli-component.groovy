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
def MASTER_RECIPIENTS_LIST = "team-mfaas-darkside.pdl@broadcom.com"

/**
 * Constants to be used throughout the pipeline
 */
def CONST = [
    // Project scope
    scope: '@zowe',
    // Artifactory URL
    artifactory: 'https://zowe.jfrog.io/zowe/api/npm/npm-local-release/',
    // Artifactory credential ID
    artifactoryId: 'zowe.jfrog.io',
    // Public registry
    distRegistry: 'https://registry.npmjs.org/',
    // Distribution Registry credential ID
    distId: 'zowe-robot-public-npmjs-registry'
]
def rmProt(String url) {
    return url.contains("https") ? url.replace("https://", "") : url.replace("http://", "");
}

def opts = []
opts.push(
    parameters([
        string(name: 'PKG_NAME', defaultValue: 'cli', description: 'Name of the package to be deployed<br/><strong>Note:</strong> the <code>@zowe</code> scope will be prepended', trim: true),
        string(name: 'PKG_TAG', defaultValue: 'daily', description: 'Tag to be distributed from artifactory', trim: true),
        string(name: 'RECIPIENTS_LIST', defaultValue: '', description: 'List of emails to receive build resutls (Override)')
    ])
)
opts.push(buildDiscarder(logRotator(numToKeepStr: '20')))
properties(opts)

/**
 * This is the tag of the package that will be deployed.
 * This field is populated by the pipeline.
 * This value is used as part of the subject in the email that gets sent.
 */
def PKG_TAG = ""

/**
 * This is the version package that will be deployed.
 * This field is populated by the pipeline.
 * This value is used as part of the subject in the email that gets sent.
 */
def PKG_VERSION = ""

/**
 * This is the version package that will be deployed.
 * This field is populated by the pipeline.
 */
def OLD_PKG_VER = ""

/**
 * This variable indicates if we got something new that we should publish.
 * This value is used to alter the details of the email that gets sent.
 */
def VERSIONS_MATCH = true

import java.text.SimpleDateFormat;

node('ca-jenkins-agent') {
  try {
    checkout scm
    stage('Deploy package') {
      PKG_TAG = params.PKG_TAG
      def getPkgInfo = load 'scripts/getPackageInfo.groovy'
      sh "rm -f .npmrc || exit 0"
      sh "rm -f ~/.npmrc || exit 0"
      sh "chmod +x ./scripts/npm_login.sh"

      def tgzUrl = ""
      def viewOpts = "--${CONST.scope}:registry=${CONST.artifactory}"
      try {
        PKG_VERSION = getPkgInfo("${CONST.scope}/${params.PKG_NAME}@${PKG_TAG}", viewOpts, "version")
        tgzUrl = getPkgInfo("${CONST.scope}/${params.PKG_NAME}@${PKG_TAG}", viewOpts, "dist.tarball")
      } catch (e) {
        error "${e.getMessage()}"
      }

      def fullPkgName = "${params.PKG_NAME}-${PKG_VERSION}.tgz"
      // Download the tgz file
      sh "curl --silent \"${tgzUrl}\" > ${fullPkgName}"

      withCredentials([usernamePassword(credentialsId: CONST.distId, usernameVariable: 'USERNAME', passwordVariable: 'TOKEN')]) {
        sh "echo \"//${rmProt(CONST.distRegistry)}:_authToken=$TOKEN\" > ~/.npmrc"
        sh "echo \"registry=${CONST.distRegistry}\" >> ~/.npmrc"
      }

      try {
          OLD_PKG_VER = getPkgInfo("${CONST.scope}/${params.PKG_NAME}@${PKG_TAG}")
      } catch (e) {
          // Do not error out
          echo "${e.getMessage()}"
      }

      if (OLD_PKG_VER == PKG_VERSION) {
        VERSIONS_MATCH = true
        echo "Package: ${CONST.scope}/${params.PKG_NAME}@${PKG_VERSION} already exists"
      } else {
        try {
          OLD_PKG_VER = getPkgInfo("${CONST.scope}/${params.PKG_NAME}@${PKG_VERSION}")
          VERSIONS_MATCH = true
          echo "Package: ${CONST.scope}/${params.PKG_NAME}@${PKG_VERSION} already exists, adding tag ${PKG_TAG}"
          sh "npm dist-tag add ${CONST.scope}/${params.PKG_NAME}@${PKG_VERSION} ${PKG_TAG}"
        } catch (e) {
          VERSIONS_MATCH = false
          sh "chmod +x ./scripts/repackage_tar.sh"
          sh "./scripts/repackage_tar.sh ${fullPkgName} ${CONST.distRegistry} ${PKG_VERSION}"
          if (PKG_TAG != PKG_VERSION) {
            sh "npm publish ${fullPkgName} --tag ${PKG_TAG} --access public"
          } else {
            sh "npm publish ${fullPkgName} --access public"
          }
          sh "rm -f ~/.npmrc || exit 0"
        }
      }
    }
  } catch (e) {
    currentBuild.result = 'FAILURE'
    throw e
  } finally {
    def buildStatus = currentBuild.currentResult
    def EXTRA_RECIPIENTS_LIST = "cc:Sujay.Solomon@broadcom.com, cc:Michael.Bauer2@broadcom.com, cc:Daniel.Kelosky@broadcom.com, cc:Mark.Ackert@broadcom.com, cc:Fernando.RijoCedeno@broadcom.com"
    def recipients = "${MASTER_RECIPIENTS_LIST}, ${EXTRA_RECIPIENTS_LIST}"
    def pkgDesc = "${CONST.scope}/${params.PKG_NAME}@${PKG_TAG}: ${PKG_VERSION}"
    def details = "Deployment of the package ${pkgDesc}"

    try {
      if (buildStatus.equals("SUCCESS")) {
        if (VERSIONS_MATCH) {
          details = "${details} was not required."
          // Prevent sendind undesired emails to everyone
          recipients = ""
        } else {
          details = "${details} has been placed in ${CONST.distRegistry}"
        }
      } else {
        details = "${details} failed.\n\nPlease investigate build ${currentBuild.number}"
      }
      details = "${details}\n\nBuild result: ${currentBuild.absoluteUrl}"

      if (params.RECIPIENTS_LIST != "") {
        recipients = "${params.RECIPIENTS_LIST}, cc:fernando.rijocedeno@broadcom.com"
      }

      if (recipients != "") {
        emailext(to: recipients, subject: "Deploy package: ${pkgDesc} status: ${buildStatus}", body: details)
      } else {
        echo "${details}"
      }
    } catch (e) {
      echo "Experienced an error sending an email for a ${buildStatus} build"
      currentBuild.result = buildStatus
    }
  }
}
