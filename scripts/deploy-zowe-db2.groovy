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
    artifactory: 'https://gizaartifactory.jfrog.io/gizaartifactory/api/npm/npm-local-release/',
    // Artifactory credential ID
    artifactoryId: 'GizaArtifactory',
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
        string(name: 'PKG_NAME', defaultValue: 'db2', description: 'Name of the package to be deployed'),
        string(name: 'PKG_TAG', defaultValue: 'daily', description: 'Tage to be distributed from artifactory'),
        string(name: 'RECIPIENTS_LIST', defaultValue: '', description: 'List of emails to recevie build resutls (Override)')
    ])
)
opts.push(buildDiscarder(logRotator(numToKeepStr: '10')))
opts.push(disableConcurrentBuilds())

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
 * This value defaults to true for informational purposes.
 * This value is used to alter the details of the email that gets sent.
 */
def VERSIONS_MATCH = true

/**
 * This variable indicates the latest stage that ran before a failure.
 * This field is populated by the pipeline.
 */
def FAILED_STAGE = ""

import java.text.SimpleDateFormat;

node('ca-jenkins-agent') {
  try {
    checkout scm
    stage('Deploy package') {
      PKG_TAG = params.PKG_TAG
      if(PKG_TAG.equals("lts-incremental") || PKG_TAG.equals("lts-stable")){
          CONST.scope = "@brightside"
      }

      FAILED_STAGE = env.STAGE_NAME
      def getPkgInfo = load 'scripts/getPackageInfo.groovy'
      sh "rm -f .npmrc || exit 0"
      sh "rm -f ~/.npmrc || exit 0"
      sh "chmod +x ./scripts/npm_login.sh"

      withCredentials([usernamePassword(credentialsId: CONST.artifactoryId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        sh "./scripts/npm_login.sh $USERNAME $PASSWORD dummy@example.com ${CONST.artifactory} ${CONST.scope}"

        def tgzUrl = ""
        def viewOpts = "--${CONST.scope}:registry=${CONST.artifactory}"
        try {
          PKG_VERSION = getPkgInfo("${CONST.scope}/${params.PKG_NAME}@${PKG_TAG}", viewOpts, "version")
          tgzUrl = getPkgInfo("${CONST.scope}/${params.PKG_NAME}@${PKG_TAG}", viewOpts, "dist.tarball")
        } catch (e) {
          error "${e.getMessage()}"
        }
        sh "npm logout --registry=${CONST.artifactory} --scope=${CONST.scope}"

        // Download the tgz file
        def artAuth = "$USERNAME:\"$PASSWORD\""
        sh "curl --silent -u$artAuth \"${tgzUrl}\" > ${params.PKG_NAME}-${PKG_VERSION}.tgz"
      }
      
      withCredentials([usernamePassword(credentialsId: CONST.distId, usernameVariable: 'USERNAME', passwordVariable: 'TOKEN')]) {
        sh "echo \"//${rmProt(CONST.distRegistry)}:_authToken=$TOKEN\" > ~/.npmrc"
        sh "echo \"registry=${CONST.distRegistry}\" >> ~/.npmrc"
      }

      try {
          OLD_PKG_VER = getPkgInfo("${CONST.scope}/${params.PKG_NAME}@${PKG_TAG}", CONST.distRegistry, "version")
      } catch (e) {
          // Do not error out
          echo "${e.getMessage()}"
      }

      if (OLD_PKG_VER == PKG_VERSION) {
        VERSIONS_MATCH = true
        echo "Package: ${CONST.scope}/${params.PKG_NAME}@${PKG_VERSION} already exists"
      } else {
        VERSIONS_MATCH = false
        def fullPkgName = "${params.PKG_NAME}-${PKG_VERSION}.tgz"
        // Repackage the tar file with the new tgz after changing the publishConfig.registry and the version
        sh "chmod +x ./scripts/repackage_tar.sh"
        sh "./scripts/repackage_tar.sh ${fullPkgName} ${CONST.distRegistry} ${PKG_VERSION}"

        // We want these package to be public
        sh "npm publish ${fullPkgName} --tag ${PKG_TAG} --access public"
        
        sh "rm -f ~/.npmrc || exit 0"
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
        recipients = params.RECIPIENTS_LIST
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
