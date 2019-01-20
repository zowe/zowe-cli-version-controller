/*
* This program and the accompanying materials are made available under the terms of the *
* Eclipse Public License v2.0 which accompanies this distribution, and is available at *
* https://www.eclipse.org/legal/epl-v20.html                                      *
*                                                                                 *
* SPDX-License-Identifier: EPL-2.0                                                *
*                                                                                 *
* Copyright Contributors to the Zowe Project.                                     *
*                                                                                 *
*/

// The following property need to be set for the HTML report @TODO figure out how to get this nicely on jenkins
//System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")

/**
 * The root results folder for items configurable by environmental variables
 */
def TEST_RESULTS_FOLDER = "__tests__/__results__/ci"

/**
 * The location of the unit test results
 */
def UNIT_RESULTS = "${TEST_RESULTS_FOLDER}/unit"

/**
 * The location of the integration test results
 */
def INTEGRATION_RESULTS = "${TEST_RESULTS_FOLDER}/integration"

/**
 * The name of the master branch
 */
def MASTER_BRANCH = "master"

/**
* Is this a release branch? Temporary workaround that won't break everything horribly if we merge.
*/
def RELEASE_BRANCH = false

/**
 * List of people who will get all emails for master builds
 */
def MASTER_RECIPIENTS_LIST = "cc:christopher.wright@broadcom.com, cc:fernando.rijocedeno@broadcom.com, cc:michael.bauer2@broadcom.com, cc:mark.ackert@broadcom.com, cc:daniel.kelosky@broadcom.com"

/**
 * The result string for a successful build
 */
def BUILD_SUCCESS = 'SUCCESS'

/**
 * The result string for an unstable build
 */
def BUILD_UNSTABLE = 'UNSTABLE'

/**
 * The result string for a failed build
 */
def BUILD_FAILURE = 'FAILURE'

/**
 * The user's name for git commits
 */
def GIT_USER_NAME = 'zowe-robot'

/**
 * The user's email address for git commits
 */
def GIT_USER_EMAIL = 'zowe.robot@gmail.com'

/**
 * The base repository url for github
 */
def GIT_REPO_URL = 'github.com/zowe/zowe-cli.git'

/**
 * The credentials id field for the authorization token for GitHub stored in Jenkins
 */
def GIT_CREDENTIALS_ID = 'zowe-robot-github'

/**
 * A command to be run that gets the current revision pulled down
 */
def GIT_REVISION_LOOKUP = 'git log -n 1 --pretty=format:%h'

/**
 * The credentials id field for the artifactory username and password
 */
def ARTIFACTORY_CREDENTIALS_ID = 'GizaArtifactory'

/**
 * The email address for the artifactory
 */
def ARTIFACTORY_EMAIL = GIT_USER_EMAIL

/**
 * This is the product name used by the build machine to store information about
 * the builds
 */
def PRODUCT_NAME = "Zowe CLI"


pipeline {
    agent {
        label 'ca-jenkins-agent'
    }

    environment {
        // Environment variable for flow control. Tells most of the steps if they should build.
        SHOULD_BUILD = "true"

        // Environment variable for flow control. Indicates if the git source was updated by the pipeline.
        GIT_SOURCE_UPDATED = "false"
    }

    stages {
        /************************************************************************
         * STAGE
         * -----
         * Check for CI Skip
         *
         * TIMEOUT
         * -------
         * 2 Minutes
         *
         * EXECUTION CONDITIONS
         * --------------------
         * - Always
         *
         * DECRIPTION
         * ----------
         * Checks for the [ci skip] text in the last commit. If it is present,
         * the build is stopped. Needed because the pipeline does do some simple
         * git commits on the master branch for the purposes of version bumping.
         *
         * OUTPUTS
         * -------
         * SHOULD_BUILD will be set to 'false' if [ci skip] is found in the
         * commit text.
         ************************************************************************/
        stage('Check for CI Skip') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    script {
                        // We need to keep track of the current commit revision. This is to prevent the condition where
                        // the build starts on master and another branch gets merged to master prior to version bump
                        // commit taking place. If left unhandled, the version bump could be done on latest master branch
                        // code which would already be ahead of this build.
                        BUILD_REVISION = sh returnStdout: true, script: GIT_REVISION_LOOKUP

                        // This checks for the [ci skip] text. If found, the status code is 0
                        result = sh returnStatus: true, script: 'git log -1 | grep \'.*\\[ci skip\\].*\''
                        if (result == 0) {
                            echo '"ci skip" spotted in the git commit. Aborting.'
                            SHOULD_BUILD = "false"
                        }
                    }
                }
            }
        }
        /************************************************************************
         * STAGE
         * -----
         * Test: Unit
         *
         * TIMEOUT
         * -------
         * 10 Minutes
         *
         * EXECUTION CONDITIONS
         * --------------------
         * - SHOULD_BUILD is true
         * - The build is still successful
         *
         *
         * DESCRIPTION
         * -----------
         * Executes the `mvn test` command to perform unit tests and
         * captures the resulting html and junit outputs.
         *
         * OUTPUTS
         * -------
         * Jenkins: Unit Test Report (through junit plugin)
         * HTML: Unit Test Report
         * HTML: Unit Test Code Coverage Report
         ************************************************************************/
        stage('Test') {
            when {
                expression {
                    return SHOULD_BUILD == 'true'
                }
            }
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    echo 'Unit Test'

                    sh "chmod +x ./gradlew" // mark gradlew executable
                    // Run tests but don't fail if the script reports an error
                    sh "./gradlew test --stacktrace || exit 0"

                    // Capture test report
                    junit "build/test-results/test/TEST-org.zowe.pipelines.NodeJsTest.xml"

                }
            }
        }

    }
    post {
        /************************************************************************
         * POST BUILD ACTION
         *
         * Sends out emails and logs out of the registry
         *
         * Emails are only sent out when SHOULD_BUILD is true.
         *
         * Sends out emails when any of the following are true:
         *
         * - It is the first build for a new branch
         * - The build is successful but the previous build was not
         * - The build failed or is unstable
         * - The build is on the MASTER_BRANCH
         *
         * In the case that an email was sent out, it will send it to individuals
         * who were involved with the build and if broken those involved in
         * breaking the build. If this build is for the MASTER_BRANCH, then an
         * additional set of individuals will also get an email that the build
         * occurred.
         ************************************************************************/
        always {
            script {

                def buildStatus = currentBuild.currentResult

                if (SHOULD_BUILD == 'true') {
                    try {
                        def previousBuild = currentBuild.getPreviousBuild()
                        def recipients = ""

                        def subject = "${currentBuild.currentResult}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
                        def consoleOutput = """
                        <p>Branch: <b>${BRANCH_NAME}</b></p>
                        <p>Check console output at "<a href="${RUN_DISPLAY_URL}">${env.JOB_NAME} [${env.BUILD_NUMBER}]</a>"</p>
                        """

                        def details = ""

                        if (previousBuild == null) {
                            details = "<p>Initial build for new branch.</p>"
                        } else if (currentBuild.resultIsBetterOrEqualTo(BUILD_SUCCESS) && previousBuild.resultIsWorseOrEqualTo(BUILD_UNSTABLE)) {
                            details = "<p>Build returned to normal.</p>"
                        }

                        // Issue #53 - Previously if the first build for a branch failed, logs would not be captured.
                        //             Now they do!
                        if (currentBuild.resultIsWorseOrEqualTo(BUILD_UNSTABLE)) {
                            // Archives any test artifacts for logging and debugging purposes
                            archiveArtifacts allowEmptyArchive: true, artifacts: '__tests__/__results__/**/*.log'
                            details = "${details}<p>Build Failure.</p>"
                        }

                        if (BRANCH_NAME == MASTER_BRANCH) {
                            recipients = MASTER_RECIPIENTS_LIST

                            details = "${details}<p>A build of master has finished.</p>"

                            if (GIT_SOURCE_UPDATED == "true") {
                                details = "${details}<p>The pipeline was able to automatically bump the pre-release version in git</p>"
                            } else {
                                // Most likely another PR was merged to master before we could do the commit thus we can't
                                // have the pipeline automatically do it
                                details = """${details}<p>The pipeline was unable to automatically bump the pre-release version in git.
                                <b>THIS IS LIKELY NOT AN ISSUE WITH THE BUILD</b> as all the tests have to pass to get to this point.<br/><br/>

                                <b>Possible causes of this error:</b>
                                <ul>
                                    <li>A commit was made to <b>${MASTER_BRANCH}</b> during the current run.</li>
                                    <li>The user account tied to the build is no longer valid.</li>
                                    <li>The remote server is experiencing issues.</li>
                                </ul>

                                <i>THIS BUILD WILL BE MARKED AS A FAILURE AS WE CANNOT GUARENTEE THAT THE PROBLEM DOES NOT LIE IN THE
                                BUILD AND CORRECTIVE ACTION MAY NEED TO TAKE PLACE.</i>
                                </p>"""
                            }
                        }

                        if (details != "") {
                            echo "Sending out email with details"
                            emailext(
                                    subject: subject,
                                    to: recipients,
                                    body: "${details} ${consoleOutput}",
                                    mimeType: "text/html",
                                    recipientProviders: [[$class: 'DevelopersRecipientProvider'],
                                                         [$class: 'UpstreamComitterRecipientProvider'],
                                                         [$class: 'CulpritsRecipientProvider'],
                                                         [$class: 'RequesterRecipientProvider']]
                            )
                        }
                    } catch (e) {
                        echo "Experienced an error sending an email for a ${buildStatus} build"
                        currentBuild.result = buildStatus
                    }
                }
            }
        }
    }
}
