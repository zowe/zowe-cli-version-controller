/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 *
 */

/**
 * List of people who will get all emails for master builds
 */
def MASTER_RECIPIENTS_LIST = "christopher.boehm@broadcom.com, fernando.rijocedeno@broadcom.com"

/**
 * Options for the pipeline
 */
def opts = []
opts.push(
    parameters([
        string(name: 'SOURCE', defaultValue: 'master', description: 'Source branch'),
        string(name: 'TARGET', defaultValue: 'latest', description: 'Target branch'),
        string(name: 'GIT_URL', defaultValue: 'https://github.com/zowe/zowe-cli.git', description: 'Git project'),
        string(name: 'GIT_CRED_ID', defaultValue: 'zowe-robot-github', description: 'Git Credential ID'),
        string(name: 'GIT_EMAIL', defaultValue: 'zowe.robot@gmail.com', description: 'Git email'),
        string(name: 'FILE_OVERWRITE', defaultValue: 'package.json', description: 'comma-separated file(s) that will have conflicts'),
        string(name: 'RECIPIENTS_LIST', defaultValue: '', description: 'List of emails to recevie build resutls (Override)')
    ])
)
opts.push(buildDiscarder(logRotator(numToKeepStr: '10')))
opts.push(disableConcurrentBuilds())
properties(opts)

/**
 * Constants to be used throughout the pipeline
 */
def CONST = [
    // Git origin
    origin: "origin",
    // Commit message
    message: "Fix conflicts ahead-of-time [ci skip]"
]

node('ca-jenkins-agent') {
    try {
        stage('Propagate branch') {
            sh "git clone ${params.GIT_URL}"
            def gitDir = params.GIT_URL.split('/').last()
            gitDir = gitDir.substring(0, gitDir.length() - ".git".length())
            dir("${gitDir}"){
                sh "git checkout ${params.SOURCE}"
                sh "git status"
                sh "mkdir temp || exit 0"
                
                // Looping through the list becuase brace expansion is unsupported given unwanted escaping
                def filesVar = "package.json,.npmrc".split(',')
                for (item in filesVar){
                    sh "cp ./${item} ./temp/"
                }
                
                sh "git status"
                sh "git checkout ${params.TARGET}"
                sh "git status"
                
                for (item in filesVar){
                    sh "mv ./temp/${item} ./"
                }
                
                sh "git status"
                sh "git config user.email \"${params.GIT_EMAIL}\""
                sh "git config push.default simple"

                withCredentials([usernamePassword(credentialsId: params.GIT_CRED_ID, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                    sh "git config user.name \"$USERNAME\""
                    sh "git commit -a -m \"${CONST.message}\""
                    sh "git pull ${CONST.origin} ${params.SOURCE}"
                    sh "git status"
                    sh "git push ${params.GIT_URL.split('//', 2).join("//$USERNAME:$PASSWORD@")}"
                }
            }
        }
    } catch (e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        def recipients = params.RECIPIENTS_LIST != '' ? params.RECIPIENTS_LIST : MASTER_RECIPIENTS_LIST
        emailext(
            to: recipients,
            subject: "[${currentBuild.currentResult}] Branch propagated: ${params.SOURCE} => ${params.TARGET}",
            body: """
            <p>Repository: ${params.GIT_URL}</p>
            <p>File(s) overwritten: ${params.FILE_OVERWRITE}</p>
            <p>Build result: ${currentBuild.absoluteUrl}</p>
            """
        );
    }
}
