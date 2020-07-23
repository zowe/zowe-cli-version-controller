/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

package org.zowe.pipelines.generic

import com.cloudbees.groovy.cps.NonCPS
import org.zowe.pipelines.base.Pipeline
import org.zowe.pipelines.base.enums.ResultEnum
import org.zowe.pipelines.base.enums.StageStatus
import org.zowe.pipelines.base.models.Stage
import org.zowe.pipelines.generic.arguments.*
import org.zowe.pipelines.generic.enums.BuildType
import org.zowe.pipelines.generic.enums.GitOperation
import org.zowe.pipelines.generic.exceptions.git.*
import org.zowe.pipelines.generic.models.*
import org.zowe.pipelines.generic.exceptions.*
import java.util.regex.Pattern

/**
 * Extends the functionality available in the {@link org.zowe.pipelines.base.Pipeline} class. This class adds methods for
 * building and testing your application.
 *
 * <dl><dt><b>Required Plugins:</b></dt><dd>
 * The following plugins are required:
 *
 * <ul>
 *     <li>All plugins listed at {@link org.zowe.pipelines.base.Pipeline}</li>
 *     <li><a href="https://plugins.jenkins.io/credentials-binding">Credentials Binding</a></li>
 *     <li><a href="https://plugins.jenkins.io/junit">JUnit</a></li>
 *     <li><a href="https://plugins.jenkins.io/htmlpublisher">HTML Publisher</a></li>
 *     <li><a href="https://plugins.jenkins.io/cobertura">Cobertura</a></li>
 * </ul>
 * </dd></dl>
 *
 * @Example
 *
 * <pre>
 * {@literal @}Library('fill this out according to your setup') import org.zowe.pipelines.generic.GenericPipeline
 * node('pipeline-node') {
 *     GenericPipeline pipeline = new GenericPipeline(this)
 *
 *     // Set your config up before calling setup
 *     pipeline.admins.add("userid1", "userid2", "userid3")
 *
 *     // Define some protected branches
 *     pipeline.protectedBranches.addMap([
 *         [name: "master"],
 *         [name: "beta"],
 *         [name: "rc"]
 *     ])
 *
 *     // Define the git configuration
 *     pipeline.gitConfig = [
 *         email: 'git-user-email@example.com',
 *         credentialsId: 'git-user-credentials-id'
 *     ]
 *
 *     // MUST BE CALLED FIRST
 *     pipeline.setupGeneric()
 *
 *     pipeline.buildGeneric()  //////////////////////////////////////////////////
 *     pipeline.testGeneric()   // Provide required parameters in your pipeline //
 *     pipeline.deployGeneric() //////////////////////////////////////////////////
 *
 *     // MUST BE CALLED LAST
 *     pipeline.endGeneric()
 * }
 * </pre>
 */
class GenericPipeline extends Pipeline {
    /**
     * Text used for the CI SKIP commit.
     */
    protected static final String _CI_SKIP = "[ci skip]"

    /**
     * The token id for git credentials.
     */
    protected static final String _TOKEN = "TOKEN"

    /**
     * Stores the change information for reference later.
     */
    final ChangeInformation changeInfo

    /**
     * Git user configuration.
     *
     * <p>The configuration will determine what user is responsible for committing and pushing
     * code updates done by the pipeline. Failure to include this configuration will result in
     * a {@link org.zowe.pipelines.generic.exceptions.git.GitException} being thrown in the pipeline
     * setup.</p>
     */
    GitConfig gitConfig

    /**
     * More control variables for the pipeline.
     */
    protected GenericPipelineControl _control = new GenericPipelineControl()

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the GenericPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * @Example
     * <pre>
     * def pipeline = new GenericPipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    GenericPipeline(steps) {
        super(steps)
        changeInfo = new ChangeInformation(steps)
    }

    /**
     * Creates a stage that will build a generic package.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link BuildStageArguments} class. The
     * {@link BuildStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Build: {@link BuildStageArguments#name}</b></dt>
     *     <dd>
     *         Runs the build of your application. The build stage also ignores any
     *         {@link BuildStageArguments#resultThreshold} provided and only runs
     *         on {@link ResultEnum#SUCCESS}.</p>
     *     </dd>
     * </dl>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions can be thrown by the build stage:
     *
     *     <dl>
     *         <dt><b>{@link BuildStageException}</b></dt>
     *         <dd>When arguments.stage is provided. This is an invalid argument field for the operation.</dd>
     *         <dd>When called more than once in your pipeline. Only one build may be present in a
     *             pipeline.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When arguments.operation is not provided.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code build} but had to be named
     * {@code buildGeneric} due to the issues described in {@link org.zowe.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link BuildStageArguments} used to define
     *                  the stage.
     */
    void buildGeneric(Map arguments = [:]) {
        BuildStageException preSetupException

        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        BuildStageArguments args = arguments

        if (_control.build) {
            preSetupException = new BuildStageException("Only one build step is allowed per pipeline.", args.name)
        } else if (args.stage) {
            preSetupException = new BuildStageException("arguments.stage is an invalid option for buildGeneric", args.name)
        }

        args.name = "Build: ${args.name}"
        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            args.operation(stageName)
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage build = createStage(args)
        if (!_control.build) {
            _control.build = build
        }
    }

    /**
     * Creates a stage that will execute a version bump
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link VersionStageArguments} class. The
     * {@link VersionStageArguments#operation} will be executed after all checks are complete. This must
     * be provided or a {@link java.lang.NullPointerException} will be encountered.</p>
     *
     * @Stages
     * This method adds the following stage to your build:
     * <dl>
     *     <dt><b>Versioning: {@link VersionStageArguments#name}</b></dt>
     *     <dd>This stage is responsible for bumping the version of your application source.</dd>
     * </dl>
     *
     * @Conditions
     *
     * <p>
     *     This stage will adhere to the following conditions:
     *
     *     <ul>
     *         <li>The stage will only execute if the current build result is {@link ResultEnum#SUCCESS} or higher.</li>
     *         <li>The stage will only execute if the current branch is protected.</li>
     *     </ul>
     * </p>
     *
     * @Exceptions
     *
     * <p>
     *     The following exceptions will be thrown if there is an error.
     *
     *     <dl>
     *         <dt><b>{@link VersionStageException}</b></dt>
     *         <dd>When stage is provided as an argument.</dd>
     *         <dd>When a test stage has not executed.</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When an operation is not provided for the stage.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code version} but had to be named
     * {@code versionGeneric} due to the issues described in {@link org.zowe.pipelines.base.Pipeline}.
     *
     * @param arguments A map of arguments to be applied to the {@link VersionStageArguments} used to define the stage.
     */
    void versionGeneric(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        GenericStageArguments args = arguments as GenericStageArguments

        VersionStageException preSetupException

        if (args.stage) {
            preSetupException = new VersionStageException("arguments.stage is an invalid option for versionGeneric", args.name)
        }

        args.name = "Versioning${arguments.name ? ": ${arguments.name}" : ""}"

        // Execute the stage if this is a protected branch and the original should execute function are both true
        args.shouldExecute = {
            boolean shouldExecute = true

            if (arguments.shouldExecute) {
                shouldExecute = arguments.shouldExecute()
            }

            return shouldExecute && _isProtectedBranch
        }

        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            if (_control.build?.status != StageStatus.SUCCESS) {
                throw new VersionStageException("Build must be successful to deploy", args.name)
            } else if (_control.preDeployTests && _control.preDeployTests.findIndexOf {it.status <= StageStatus.FAIL} != -1) {
                throw new VersionStageException("All test stages before versioning must be successful or skipped!", args.name)
            } else if (_control.preDeployTests.size() == 0) {
                throw new VersionStageException("At least one test stage must be defined", args.name)
            }

            args.operation(stageName)
        }

        // Create the stage and ensure that the first one is the stage of reference
        Stage version = createStage(args)
        if (!_control.version) {
            _control.version = version
        }
    }

    /**
     * Creates a stage that will execute a version bump and then deploy. test
     *
     * @Stages
     * This method can add 2 stages to the build:
     *
     * <dl>
     *     <dt><b>Versioning</b></dt>
     *     <dd>This stage is responsible for bumping the version of your application source. It will only
     *         be added if <b>versionArguments</b> is a non-empty map.</dd>
     *     <dt><b>Deploy</b></dt>
     *     <dd>This stage is responsible for deploying your application source. It will always execute
     *         after Versioning (if present).</dd>
     * </dl>
     *
     * @Conditions
     *
     * <p>
     *     Both stages will adhere to the following conditions:
     *
     *     <ul>
     *         <li>The stage will only execute if the current build result is
     *         {@link ResultEnum#SUCCESS} or higher.</li>
     *         <li>The stage will only execute if the current branch is protected.</li>
     *     </ul>
     * </p>
     *
     * @Exceptions
     *
     * <p>
     *     Both the Version stage and the Deploy stage will throw the following exceptions:
     *
     *     <dl>
     *         <dt><b>{@link DeployStageException}</b></dt>
     *         <dd>When stage is provided as an argument. This is an invalid parameter for both
     *             stages</dd>
     *         <dd>When a test stage has not executed. This prevents untested code from being
     *             deployed</dd>
     *         <dt><b>{@link NullPointerException}</b></dt>
     *         <dd>When an operation is not provided for the stage.</dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code deploy} but had to be named
     * {@code deployGeneric} due to the issues described in {@link org.zowe.pipelines.base.Pipeline}.
     *
     * @param deployArguments The arguments for the deploy step. {@code deployArguments.operation} must be
     *                        provided.
     * @param versionArguments The arguments for the versioning step. If provided, then
     *                         {@code versionArguments.operation} must be provided.
     */
    void deployGeneric(Map deployArguments, Map versionArguments = [:]) {
        if (versionArguments.size() > 0) {
            versionGeneric(versionArguments)
        }

        deployArguments.resultThreshold = ResultEnum.SUCCESS

        GenericStageArguments args = deployArguments as GenericStageArguments

        args.name = "Deploy${deployArguments.name ? ": ${deployArguments.name}" : ""}"

        DeployStageException preSetupException

        if (args.stage) {
            preSetupException = new DeployStageException("arguments.stage is an invalid option for deployGeneric", args.name)
        }

        // Execute the stage if this is a protected branch and the original should execute function are both true
        args.shouldExecute = {
            boolean shouldExecute = true

            if (deployArguments.shouldExecute) {
                shouldExecute = deployArguments.shouldExecute()
            }

            return shouldExecute && _isProtectedBranch
        }

        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            if (_control.build?.status != StageStatus.SUCCESS) {
                throw new DeployStageException("Build must be successful to deploy", args.name)
            } else if (_control.preDeployTests && _control.preDeployTests.findIndexOf {it.status <= StageStatus.FAIL} != -1) {
                throw new DeployStageException("All test stages before deploy must be successful or skipped!", args.name)
            } else if (_control.preDeployTests.size() == 0) {
                throw new DeployStageException("At least one test stage must be defined", args.name)
            }

            args.operation(stageName)
        }

        createStage(args)
    }

    /**
     * Signal that no more stages will be added and begin pipeline execution.
     *
     * <p>This method wraps the entire pipeline execution in a {@code withCredentials} block. The
     * credentials that are loaded represent the credentials stored in {@link #gitConfig}. This
     * will ensure that any pushes/commits don't expose the credentials in plaintext console output.
     * </p>
     *
     * @param options Options to send to {@link org.zowe.pipelines.base.Pipeline#endBase(java.util.Map)}
     */
    void endGeneric(Map options = [:]) {
        if (!gitConfig?.credentialsId) {
            if (!_stages.firstFailingStage) {
                _stages.firstFailingStage = _stages.getStage(_SETUP_STAGE_NAME)
                _stages.firstFailingStage.exception = new GitException("Git configuration not specified!")
            }
            super.endBase(options)
        } else {
            // Wrap all this in a with credentials call for security purposes
            steps.withCredentials([steps.usernameColonPassword(
                credentialsId: gitConfig.credentialsId, variable: _TOKEN
            )]) {
                super.endBase(options)
            }
        }
    }

    /**
     * Commit a code change during pipeline execution.
     *
     * <p>If no changes were detected, the commit will not happen. If a commit occurs, the end of
     * of the commit message will be appended with the ci skip text.</p>
     * @param message The commit message
     * @param amend Indicates if the commit should amend the previous commit
     * @return A boolean indicating if a commit was made. True indicates that a successful commit
     *         has occurred.
     * @throw {@link IllegalBuildException} when a commit operation happens on an illegal build type.
     */
    boolean gitCommit(String message, boolean amend = false) {
        if (changeInfo.isPullRequest) {
            throw new IllegalBuildException(GitOperation.COMMIT, BuildType.PULL_REQUEST)
        }

        def ret = steps.sh returnStatus: true, script: "git status | grep 'Changes to be committed:'"
        steps.sh "git status"

        if (ret == 0 || amend) {
            steps.sh "git commit${amend? " --amend" : ""} -m \"$message $_CI_SKIP\" --signoff"
            return true
        } else {
            return false
        }
    }

    /**
     * Pushes any changes to the remote server
     *
     * <p>If the remote server has any changes then this method will throw an error indicating that
     * the branch is out of sync</p>
     *
     * @param tags Indicates if we also want to push tags
     * @param force Indicates if we should try to push even if there is nothing to push
     * @param forcePush Indicates if we should force-push the changes
     * @return A boolean indicating if the push was made. True indicates a successful push
     * @throw {@link IllegalBuildException} when a push operation happens on an illegal build type.
     * @throw {@link BehindRemoteException} when pushing to a branch that has forward commits from this build
     * @throw {@link GitException} when there is nothing to push
     */
    boolean gitPush(boolean tags = false, boolean force = false, boolean forcePush = false) throws GitException {
        if (changeInfo.isPullRequest) {
            throw new IllegalBuildException(GitOperation.PUSH, BuildType.PULL_REQUEST)
        }

        steps.sh "git fetch"
        String status = steps.sh returnStdout: true, script: "git status"
        steps.sh "git status"

        if (Pattern.compile("Your branch and '.*' have diverged").matcher(status).find() && !forcePush) {
            throw new BehindRemoteException("Remote branch is ahead of the local branch!", changeInfo.branchName)
        } else if (Pattern.compile("Your branch is ahead of").matcher(status).find() || force || forcePush) {
            steps.sh "git push --set-upstream origin ${changeInfo.branchName} --verbose ${forcePush ? '--force' : ''}"
            if (tags) steps.sh "git push --tags"
        } else {
            throw new GitException("Nothing to push")
        }

        return true
    }

    /**
     * Tag a code version during pipeline execution.
     *
     * @param label Indicates the label to use to the tag
     * @param description The tag description
     * @return A boolean indicating if a tag was created or not.
     * @throw {@link IllegalBuildException} when a gitTag operation happens on an illegal build type.
     */
    boolean gitTag(String label, String description) {
        if (changeInfo.isPullRequest) {
            throw new IllegalBuildException(GitOperation.COMMIT, BuildType.PULL_REQUEST)
        }

        try {
            steps.sh "git tag $label -m \"$description\""
            steps.sh "git push --tags"
            return true
        } catch (Exception e) {
            // Do nothing
            return false
        }
    }

    String getLabels() {
        def labels
        def scmHead = jenkins.scm.api.SCMHead.HeadByItem.findHead(steps.currentBuild.rawBuild.getParent())
        def repo = scmHead.getSourceRepo()
        def prId = scmHead.getId()

        def scmUrl = steps.scm.getUserRemoteConfigs()[0].getUrl()
        if (scmUrl.contains("github.com")) {
            steps.withCredentials([steps.usernamePassword(credentialsId: gitConfig.credentialsId, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
                def json = steps.sh(returnStdout: true, script: "curl -u \$USERNAME:\$PASSWORD https://api.github.com/repos/zowe/${repo}/issues/${prId}")
                def prInfo = steps.readJSON(text: json)
                labels = prInfo.labels
            }
        } else {
          labels = null
        }
        return labels
    }


    /**
     * Verify that the changelog has been modified.
     *
     * @param file Indicates the file to be checked
     * @param lines Indicates the number of lines to check for the header
     * @param header Indicates the header that should exist in the changelog
     * @return void
     */

    void checkChangelog(Map arguments = [:]) {
        ChangelogStageArguments args = arguments
        if (changeInfo.isPullRequest) {
            createStage(name: "Check Changelog", stage: {
                try {
                    def fetchOutput = steps.sh(returnStdout: true, script: "git --no-pager fetch 2>&1 || exit 0").trim()
                    if (fetchOutput.toLowerCase().contains("could not read username")) {
                        configureGit(true, true)
                    }
                } catch (err) {
                    steps.error "${err.message}"
                }
                String target = steps.CHANGE_TARGET
                String changedFiles = steps.sh(returnStdout: true, script: "git --no-pager diff origin/${target} --name-only").trim()
                String labels = getLabels()
                if (labels == null) {
                  steps.echo "Unable to read labels for this Pull Request. Forcing changelog check."
                }
                if (labels != null && labels.contains("no-changelog")) {
                    steps.echo "no-changelog label found on Pull Request. Skipping changelog check."
                } else if (changedFiles.contains(args.file)) {
                    def contents = steps.sh(returnStdout: true, script: "cat ${args.file}").trim()
                    if (contents.contains(args.header)) {
                        steps.echo "Header found"
                    } else {
                        steps.error "Changelog missing valid header. Please see CONTRIBUTING.md for changelog format."
                    }
                } else {
                    steps.error "Changelog has not been modified from origin/master. Please see CONTRIBUTING.md for changelog format."
                }
            })
        }
    }

    void configureGit(boolean stayInContext = false, boolean forceAuth = false) {
        steps.withCredentials([steps.usernamePassword(
            credentialsId: gitConfig.credentialsId,
            passwordVariable: "NOT_USED",
            usernameVariable: "GIT_USER_NAME"
        )]) {
            steps.sh "git config user.name \$GIT_USER_NAME"
            steps.sh "git config user.email \"${gitConfig.email}\""
            steps.sh "git config push.default simple"
        }

        // Setup the branch to track it's remote
        if (!stayInContext) steps.sh "git checkout ${changeInfo.branchName}"
        steps.sh "git status"

        // If the branch is protected, setup the proper configuration
        if (_isProtectedBranch || forceAuth) {
            String remoteUrl = steps.sh(returnStdout: true, script: "git remote get-url --all origin").trim()

            // Only execute the credential code if the url does not already contain credentials
            String remoteUrlWithCreds = remoteUrl.replaceFirst("https://", "https://\\\$$_TOKEN@")

            // Set the push url to the correct one
            steps.sh "git remote set-url --add origin $remoteUrlWithCreds"
            steps.sh "git remote set-url --delete origin $remoteUrl"
        }
    }

    /**
     * Calls {@link org.zowe.pipelines.base.Pipeline#setupBase()} to setup the build.
     *
     * @Stages
     * This method adds 2 stages to the build:
     *
     * <dl>
     *     <dt><b>Configure Git</b></dt>
     *     <dd>
     *         This step configures the git environment for commits and pushes. The {@link #gitConfig}
     *         provided will be injected into the git remote url and the head will point to the
     *         proper remote branch. The username and email settings will also be set.  If the
     *         current build is for a pull request, this step will be skipped.
     *     </dd>
     *     <dt><b>Check for CI Skip</b></dt>
     *     <dd>
     *         Checks that the build commit doesn't contain the CI Skip indicator. If the pipeline finds
     *         the skip commit, all remaining steps (except those explicitly set to ignore this condition)
     *         will also be skipped. The build will also be marked as not built in this scenario.
     *     </dd>
     * </dl>
     *
     * @Note This method was intended to be called {@code setup} but had to be named
     * {@code setupGeneric} due to the issues described in {@link org.zowe.pipelines.base.Pipeline}.
     */
    void setupGeneric(GenericSetupArguments timeouts) {
        // Call setup from the super class
        super.setupBase(timeouts)

        createStage(name: 'Configure Git', stage: {
          configureGit()
        }, isSkippable: false, timeout: timeouts.gitSetup, shouldExecute: {
            // Disable commits and pushes
            return !changeInfo.isPullRequest
        })

        createStage(name: 'Check for CI Skip', stage: {
            // This checks for the [ci skip] text. If found, the status code is 0
            def result = steps.sh returnStatus: true, script: 'git log -1 | grep \'.*\\[ci skip\\].*\''
            if (result == 0) {
                steps.echo "\"${_CI_SKIP}\" spotted in the git commit. Aborting."
                _shouldSkipRemainingStages = true
                setResult(ResultEnum.NOT_BUILT)
            }
        }, timeout: timeouts.ciSkip)
    }

    /**
     * Initialize the pipeline.
     *
     * @param timeouts A map that can be instantiated as {@link GenericSetupArguments}
     * @see #setupGeneric(GenericSetupArguments)
     */
    void setupGeneric(Map timeouts = [:]) {
        setupGeneric(timeouts as GenericSetupArguments)
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Arguments passed to this function will map to the
     * {@link org.zowe.pipelines.generic.arguments.TestStageArguments} class.</p>
     *
     * @Stages
     * This method adds the following stage to the build:
     *
     * <dl>
     *     <dt><b>Test: {@link org.zowe.pipelines.generic.arguments.TestStageArguments#name}</b></dt>
     *     <dd>
     *         <p>Runs one of your application tests. If the test operation throws an error, that error is
     *         ignored and  will be assumed to be caught in the junit processing. Some test functions may
     *         exit with a non-zero return code on a test failure but may still capture junit output. In
     *         this scenario, it is assumed that the junit report is either missing or contains failing
     *         tests. In the case that it is missing, the build will fail on this report and relevant
     *         exceptions are printed. If the junit report contains failing tests, the build will be marked
     *         as unstable and a report of failing tests can be viewed.</p>
     *
     *         <p>The following reports can be captured:</p>
     *         <dl>
     *             <dt><b>Test Results HTML Report (REQUIRED)</b></dt>
     *             <dd>
     *                 This is an html report that contains the result of the build. The report must be defined to
     *                 the method in the {@link TestStageArguments#testResults} variable.
     *             </dd>
     *             <dt><b>Code Coverage HTML Report</b></dt>
     *             <dd>
     *                 This is an HTML report generated from code coverage output from your build. The report can
     *                 be omitted by omitting {@link TestStageArguments#coverageResults}
     *             </dd>
     *             <dt><b>JUnit Report (REQUIRED)</b></dt>
     *             <dd>
     *                 This report feeds Jenkins the data about the current test run. It can be used to mark a build
     *                 as failed or unstable. The report location must be present in
     *                 {@link TestStageArguments#junitOutput}
     *             </dd>
     *             <dt><b>Cobertura Report</b></dt>
     *             <dd>
     *                 This report feeds Jenkins the data about the coverage results for the current test run. If
     *                 no Cobertura options are passed, then no coverage data will be collected. For more
     *                 information, see {@link TestStageArguments#cobertura}
     *             </dd>
     *         </dl>
     *
     *         <p>
     *             The test stage will execute by default if the current build result is greater than or
     *             equal to {@link ResultEnum#UNSTABLE}. If a different status is passed, that will take
     *             precedent.
     *         </p>
     *
     *         <p>
     *             After the test is complete, the stage will continue to collect the JUnit Report and the Test
     *             Results HTML Report. The stage will fail if either of those are missing. If specified, the
     *             Code Coverage HTML Report and the Cobertura Report are then captured. The build will fail if
     *             these reports are to be collected and were missing.
     *         </p>
     *
     *         <p>
     *             Some tests may also require the use of the gnome-keyring. The stage can be configured to
     *             unlock the keyring prior to the tests by passing
     *             {@link org.zowe.pipelines.generic.arguments.TestStageArguments#shouldUnlockKeyring} as true.
     *         </p>
     *     </dd>
     * </dl>
     *
     * @Exceptions
     * <p>
     *     The test stage can throw the following exceptions:
     *
     *     <dl>
     *         <dt><b>{@link TestStageException}</b></dt>
     *         <dd>When a test stage is created before a call to {@link #buildGeneric(Map)}</dd>
     *         <dd>When {@link org.zowe.pipelines.generic.arguments.TestStageArguments#testResults} is missing</dd>
     *         <dd>When invalid options are specified for {@link org.zowe.pipelines.generic.arguments.TestStageArguments#testResults}</dd>
     *         <dd>When {@link org.zowe.pipelines.generic.arguments.TestStageArguments#coverageResults} is provided but has an invalid format</dd>
     *         <dd>When {@link TestStageArguments#junitOutput} is missing.</dd>
     *         <dd>When {@link TestStageArguments#operation} is missing.</dd>
     *         <dd>
     *     </dl>
     * </p>
     *
     * @Note This method was intended to be called {@code test} but had to be named
     * {@code testGeneric} due to the issues described in {@link org.zowe.pipelines.base.Pipeline}.</p>
     *
     * @param arguments A map of arguments to be applied to the {@link org.zowe.pipelines.generic.arguments.TestStageArguments} used to define
     *                  the stage.
     */
    void testGeneric(Map arguments = [:]) {
        // Default the resultThreshold to unstable for tests,
        // if a custom value is passed then that will be used instead
        if (!arguments.resultThreshold) {
            arguments.resultThreshold = ResultEnum.UNSTABLE
        }

        TestStageArguments args = arguments

        TestStageException preSetupException

        if (args.stage) {
            preSetupException = new TestStageException("arguments.stage is an invalid option for testGeneric", args.name)
        }

        args.name = "Test: ${args.name}"
        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            } else if (_control.build?.status != StageStatus.SUCCESS) {
                throw new TestStageException("Tests cannot be run before the build has completed", args.name)
            }

            steps.echo "Processing Arguments"

            if (!args.testResults) {
                throw new TestStageException("Test Results HTML Report not provided", args.name)
            } else {
                _validateReportInfo(args.testResults, "Test Results HTML Report", args.name)
            }

            if (!args.coverageResults) {
                steps.echo "Code Coverage HTML Report not provided...report ignored"
            } else {
                _validateReportInfo(args.coverageResults, "Code Coverage HTML Report", args.name)
            }

            if (!args.junitOutput) {
                throw new TestStageException("JUnit Report not provided", args.name)
            }

            // Unlock the keyring for dbus
            if (args.shouldUnlockKeyring) {
                steps.sh "echo 'jenkins' | gnome-keyring-daemon --unlock"
            }

            if (!args.operation) {
                throw new DeployStageException("Missing test operation!", args.name)
            }

            try {
                args.operation(stageName)
            } catch (Exception exception) {
                // If the script exited with code 143, that indicates a SIGTERM event was
                // captured. If this is the case then the process was killed by Jenkins.
                if (exception.message == "script returned exit code 143") {
                    throw exception
                }

                steps.echo "Exception: ${exception.message}"
            }

            // Collect junit report
            steps.junit args.junitOutput

            // Collect Test Results HTML Report
            steps.publishHTML(target: [
                    allowMissing         : false,
                    alwaysLinkToLastBuild: true,
                    keepAll              : true,
                    reportDir            : args.testResults.dir,
                    reportFiles          : args.testResults.files,
                    reportName           : args.testResults.name
            ])

            // Collect coverage if applicable
            if (args.coverageResults) {
                steps.publishHTML(target: [
                        allowMissing         : false,
                        alwaysLinkToLastBuild: true,
                        keepAll              : true,
                        reportDir            : args.coverageResults.dir,
                        reportFiles          : args.coverageResults.files,
                        reportName           : args.coverageResults.name
                ])
            }

            // Collect cobertura coverage if specified
            if (args.cobertura) {
                steps.cobertura(TestStageArguments.coberturaDefaults + args.cobertura)
            } else if (args.coverageResults) {
                steps.echo "WARNING: Cobertura file not detected, skipping"
            }
        }

        // Create the stage and ensure that the tests are properly added.
        Stage test = createStage(args)
        if (!(_control.version || _control.deploy)) {
            _control.preDeployTests += test
        }
    }

    /**
     * Validates that a test report has the required options.
     *
     * @param report The report to validate
     * @param reportName The name of the report being validated
     * @param stageName The name of the stage that is executing.
     *
     * @throw {@link TestStageException} when any of the report properties are invalid.
     */
    protected static void _validateReportInfo(TestReport report, String reportName, String stageName) {
        if (!report.dir) {
            throw new TestStageException("${reportName} is missing property `dir`", stageName)
        }

        if (!report.files) {
            throw new TestStageException("${reportName} is missing property `files`", stageName)
        }

        if (!report.name) {
            throw new TestStageException("${reportName} is missing property `name`", stageName)
        }
    }
}
