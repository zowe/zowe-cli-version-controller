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

/**
 * Options for the pipeline
 */
def opts = []
opts.push(
  parameters([
    string(name: 'ORG', defaultValue: 'zowe', description: 'Organization to gather packages from'),
    string(name: 'EXTRA_REPOS', defaultValue: 'zowe/vscode-extension-for-zowe:master', description: 'Add additional repositories on Public GitHub<br> It needs to be comma-separated<br>Format: user/repo-name:branch'),
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
  if (!temp) throw "Unexpected JSON format: $_temp"
  return temp
}

def getFileName(isRepo, name, tempEnv, pkgTag, format = 'json') {
  if (isRepo) {
    return "repo.$name.$tempEnv.$pkgTag.$format"
  } else {
    return "pkg.$name.$tempEnv.$pkgTag.$format"
  }
}

def _reportHelper(isRepo, name, pkgJson, pkgTag) {
  def deps = pkgJson.dependencies
  def devDeps = pkgJson.devDependencies
  sh "rm -rf $name || exit 0"
  sh "mkdir $name"
  dir(name) {
    writeJSON json: pkgJson, file: "package.json"
    sh "npm install --package-lock-only"
    sh "npm audit --json > ../${getFileName(isRepo, name, 'all', pkgTag)} || exit 0"
    sh "mkdir dev prod"

    pkgJson.dependencies = [:]
    pkgJson.devDependencies = devDeps
    dir("dev") {
      writeJSON json: pkgJson, file: "package.json"
      sh "npm install --package-lock-only"
      sh "npm audit --json > ../../${getFileName(isRepo, name, 'dev', pkgTag)} || exit 0"
    }

    pkgJson.dependencies = deps
    pkgJson.devDependencies = [:]
    dir("prod") {
      writeJSON json: pkgJson, file: "package.json"
      sh "npm install --package-lock-only"
      sh "npm audit --json > ../../${getFileName(isRepo, name, 'prod', pkgTag)} || exit 0"
    }
  }
}

/* TODO: Fix the formating
NpmAuditReports/zowe-cli/lts-incremental/pkg-name.all.json
NpmAuditReports/zowe-cli/lts-incremental/pkg-name.prod.json
NpmAuditReports/zowe-cli/lts-incremental/pkg-name.dev.json
NpmAuditReports/zowe-cli/latest/pkg-name.all.json
NpmAuditReports/zowe-cli/latest/pkg-name.prod.json
NpmAuditReports/zowe-cli/latest/pkg-name.dev.json
*/

def generateReport(name, isRepo = false) {
  return {
    stage("Generate report: $name") {
      if (isRepo) {
        def branchName = "${name.split(':')[1]}" // get branch name
        name = "${name.split(':')[0]}" // remove branch name
        def pkgJson = expectJSON("curl https://raw.githubusercontent.com/$name/$branchName/package.json")
        name = name.split('/')[1] // remove user name
        _reportHelper(isRepo, name, pkgJson, branchName)
      } else {
        def tags = expectJSON("npm view $name dist-tags --json")
        tags.each { tag, ver ->
          def pkgJson = expectJSON("npm view $name@$tag --json")
          _reportHelper(isRepo, name.split('/')[1], pkgJson, tag)
        }
      }
    }
  }
}

node('zowe-jenkins-agent') {
  def buildStages = [:]
  try {
    stage("Setup") {
      def nvmInstall = load 'scripts/nvmInstall.groovy'
      nvmInstall()
      def orgPkgs = expectJSON("npm access ls-packages @${params.ORG} --json")
      orgPkgs.each{ pkgName, perm ->
        buildStages.put(pkgName, generateReport(pkgName))
      }
      params.EXTRA_REPOS?.split(',').each{ repoName ->
        buildStages.put(repoName, generateReport(repoName, true))
      }
      sh "npm config set @brightside:registry https://api.bintray.com/npm/ca/brightside"
    }
    stage("Generate reports") {
      parallel(buildStages)
    }
    stage("Publish reports") {
      def dateTag = sh(returnStdout: true, script: "node -e \"console.log(new Date().toDateString().toLowerCase().split(/ (.+)/)[1].replace(/ /g, '-'))\"").trim()
      def reportName = "zowe-cli-json-reports.${dateTag}.tgz"
      sh "tar -czvf $reportName *.json"
      // archiveArtifacts artifacts: reportName

      def repoName = "security-reports"
      def reportBranch = "master"
      def dirName = "NpmAuditReports/zowe-cli"
      withCredentials([usernamePassword(credentialsId: 'zowe-robot-github', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        sh "git clone https://$USERNAME:$PASSWORD@github.com/zowe/$repoName"
        dir(repoName) {
          // Setup to publish reports
          sh "git checkout $reportBranch"
          sh "git config --global user.email \"zowe.robot@gmail.com\""
          sh "git config --global user.name \"zowe-robot\""
          sh "mkdir -p $dirName/json || exit 0"
          sh "cp ../$reportName $dirName/"
          sh "cp ../*.json $dirName/json/"

          dir(dirName) {
            sh "mkdir -p html || exit 0"
            sh "mkdir -p temp || exit 0"
            dir ("temp") {
              sh "npm i npm-audit-html"
              sh 'for tf in ../json/*.json; do echo "Generate: ${tf%.json}" && cat "$tf" | npx npm-audit-html -o "${tf%.json}.html"; done'
            }
            sh "rm -rf temp"
            sh "mv json/*.html html/"
          }

          // Publish reports
          sh "git add ."
          sh "git commit -m \"Add ${reportName.split('.tgz')[0]}\""
          sh "git push https://$USERNAME:$PASSWORD@github.com/zowe/$repoName $reportBranch"
        }
      }
    }
  } catch (e) {
    currentBuild.result = "FAILURE"
    throw e
  } finally {
    def recipients = params.RECIPIENTS_LIST != '' ? "${params.RECIPIENTS_LIST},fernando.rijocedeno@broadcom.com" : MASTER_RECIPIENTS_LIST
    emailext(
      to: recipients,
      subject: "[${currentBuild.currentResult}] Reports generated. ORG: ${params.ORG}",
      body: """
      ${currentBuild.currentResult} - Build result: ${currentBuild.absoluteUrl}console
      """
    );
  }
}
