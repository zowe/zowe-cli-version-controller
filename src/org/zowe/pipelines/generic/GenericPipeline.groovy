package org.zowe.pipelines.generic

import org.zowe.pipelines.base.Pipeline
import org.zowe.pipelines.base.models.ResultEnum
import org.zowe.pipelines.base.models.TimeUnit
import org.zowe.pipelines.generic.models.*
import org.zowe.pipelines.generic.exceptions.*

import java.util.concurrent.TimeUnit

/**
 * Extends the functionality available in the {@link Pipeline} class. This class adds methods for
 * building and testing your code.
 *
 * <h5>Basic Usage</h5>
 *
 * <pre>
 * // Setup is the same as the {@link Pipeline} class.
 * // ...
 * GenericPipeline pipeline = new GenericPipeline(this)
 *
 * // ...
 *
 * pipeline.setupGeneric()
 *
 * pipeline.buildGeneric()
 * pipeline.testGeneric() // Provide required parameters in your pipeline
 *
 * // MUST BE CALLED LAST
 * pipeline.endGeneric()
 * </pre>
 */
class GenericPipeline extends Pipeline {
    /**
     * Text used for the CI SKIP commit.
     */
    protected static final String _CI_SKIP = "[ci skip]"

    /**
     * Shell command that gets the current git revision.
     */
    protected static final String _GIT_REVISION_LOOKUP = "git log -n 1 --pretty=format:%h"

    /**
     * Git user configuration, add more documentation in future story
     */
    GitConfig gitConfig

    /**
     * The git commit revision of the build. This is determined at the beginning of
     * the build.
     */
    protected String _buildRevision

    /**
     * Stores the change information for reference later.
     */
    protected ChangeInformation _changeInfo

    /**
     * A boolean that tracks if the build step was run. When false, the build still hasn't completed
     */
    protected boolean _didBuild = false

    /**
     * A boolean that tracks if a single test was run. When false, there hasn't been a test run yet.
     */
    protected boolean _didTest = false

    /**
     * Constructs the class.
     *
     * <p>When invoking from a Jenkins pipeline script, the GenericPipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * <h5>Example Setup:</h5>
     * <pre>
     * def pipeline = new GenericPipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    GenericPipeline(steps) {
        super(steps)
    }

    /**
     * Creates a stage that will build a generic package.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link BuildArgs} class.</p>
     *
     * <h4>Build: {@link BuildArgs#name}</h4>
     * <p>Runs the build of your application.</p>
     *
     * <p>The build stage also ignores any {@link BuildArgs#resultThreshold} provided and only runs
     * on {@link org.zowe.pipelines.base.models.ResultEnum#SUCCESS}.</p>
     *
     * <p>This stage will throw a {@link BuildStageException} if called more than once in your pipeline.</p>
     *
     * <p><b>Note:</b> This method was intended to be called {@code build} but had to be named
     * {@code buildGeneric} due to the issues described in {@link Pipeline}.</p>
     *
     * @param arguments A map of arguments to be applied to the {@link BuildArgs} used to define
     *                  the stage.
     */
    void buildGeneric(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        BuildArgs args = arguments

        BuildStageException preSetupException

        if (args.stage) {
            preSetupException = new BuildStageException("args.stage is an invalid option for buildGeneric", args.name)
        }

        args.name = "Build: ${args.name}"
        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            if (_didBuild) {
                throw new BuildStageException("Only one build step is allowed per pipeline.", args.name)
            }

            args.operation(stageName)

            _didBuild = true
        }

        createStage(args)
    }

    // @TODO DOCUMENT
    // Versioning op happens before commit op and happens before deploy op
    void deployGeneric(Map deployArguments = [:], Map versionArguments = [:]) {
        if (deployArguments.name) {
            deployArguments.name = "Deploy: ${deployArguments.name}"
        } else {
            deployArguments.name = "Deploy"
        }

        /**
         * Creates the various stages for the deploy
         */
        Closure createSubStage = { Map arguments ->
            arguments.resultThreshold = ResultEnum.SUCCESS

            GenericArgs args = arguments

            DeployStageException preSetupException

            if (args.stage) {
                preSetupException = new DeployStageException("args.stage is an invalid option for deployGeneric", args.name)
            }

            // Execute the stage if this is a protected branch and the original should execute function
            // are both true
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

                if (!_didTest) {
                    throw new DeployStageException("A test must be run before the pipeline can deploy", args.name)
                }

                args.operation(stageName)
            }

            createStage(args)
        }


        if (versionArguments.size() > 0) {
            versionArguments.name = "Versioning"
            createSubStage(versionArguments)
        }

        createSubStage(deployArguments + [operation: { String stageName ->
            // TODO Check if we need to push any commits here and see if that would be a fast forward

            steps.sh "git status"

            // Ask user if no response default to using semver present in branch with proper prerelease branding

            deployArguments.operation(stageName)
        }])
    }

    /**
     * Commit a code change during pipeline execution.
     *
     * <p>If no changes were detected, the commit will not happen. If a commit occurs, the end of
     * of the commit message will be appended with the ci skip text.</p>
     * @param message The commit message
     * @return A boolean indicating if a commit was made. True indicates that a successful commit
     *         has occurred.
     */
    boolean gitCommit(String message) {
        def ret = steps.sh returnStatus: true, script: "git status | grep 'Changes to be committed:'"

        if (ret == 0) {
            steps.sh "git commit -m \"$message $_CI_SKIP\""
            return true
        } else {
            return false
        }
    }

    /**
     *
     */
    boolean gitPush() {
        steps.sh "git status"
        steps.sh "git push --dry-run"

        throw new Exception("ABORTING BUILD FOR TEST PURPOSES")
    }

    /**
     * Calls {@link org.zowe.pipelines.base.Pipeline#setupBase()} to setup the build.
     *
     * <p>Additionally, this method adds the following stage to the build:</p>
     *
     * @TODO ADD DOC FOR CONFIGURE GIT STAGE
     * <h4>Check for CI Skip</h4>
     *
     * <p>Checks that the build commit doesn't contain the CI Skip indicator. If the pipeline finds
     * the skip commit, all remaining steps (except those explicitly set to ignore this condition)
     * will also be skipped. The build will also be marked as not built in this scenario.</p>
     *
     * <p><b>Note:</b> This method was intended to be called {@code setup} but had to be named
     * {@code setupGeneric} due to the issues described in {@link Pipeline}.</p>
     */
    void setupGeneric() {
        // Call setup from the super class
        super.setupBase()

        createStage(name: 'Configure Git', stage: {
            _changeInfo = new ChangeInformation(steps)

            steps.sh "git config user.name \"${gitConfig.user}\""
            steps.sh "git config user.email \"${gitConfig.email}\""
            steps.sh "git config push.default simple"


            // Setup the branch to track it's remote
            steps.sh "git checkout ${_changeInfo.branchName} --track"
        }, isSkippable: false, timeout: [time: 1, unit: TimeUnit.MINUTES])

        createStage(name: 'Check for CI Skip', stage: {
            // We need to keep track of the current commit revision. This is to prevent the condition where
            // the build starts on master and another branch gets merged to master prior to version bump
            // commit taking place. If left unhandled, the version bump could be done on latest master branch
            // code which would already be ahead of this build.
            _buildRevision = steps.sh returnStatus: true, script: _GIT_REVISION_LOOKUP //@TODO this probably isn't needed anymore since we can just see how far ahead/behind we are

            // This checks for the [ci skip] text. If found, the status code is 0
            def result = steps.sh returnStatus: true, script: 'git log -1 | grep \'.*\\[ci skip\\].*\''
            if (result == 0) {
                steps.echo "\"${_CI_SKIP}\" spotted in the git commit. Aborting."
                _shouldSkipRemainingStages = true
                setResult(ResultEnum.NOT_BUILT)
            }
        }, timeout: [time: 1, unit: TimeUnit.MINUTES])
    }

    /**
     * Creates a stage that will execute tests on your application.
     *
     * <p>Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link TestArgs} class.</p>
     *
     * <h4>Test: {@link TestArgs#name}</h4>
     *
     * <p>Runs one of your application tests. If {@link TestArgs#testOperation}, the stage will execute
     * `npm run test` as the default operation. If the test operation throws an error, that error is
     * ignored and  will be assumed to be caught in the junit processing. Some test functions may
     * exit with a non-zero return code on a test failure but may still capture junit output. In
     * this scenario, it is assumed that the junit report is either missing or contains failing
     * tests. In the case that it is missing, the build will fail on this report and relevant
     * exceptions are printed. If the junit report contains failing tests, the build will be marked
     * as unstable and a report of failing tests can be viewed.</p>
     *
     * <p>The following reports can be captured:</p>
     *
     * <h5>Test Results HTML Report (REQUIRED)</h5>
     *
     * <p>This is an html report that contains the result of the build. The report must be defined to
     * the method in the {@link TestArgs#testResults} variable.</p>
     *
     * <h5>Code Coverage HTML Report</h5>
     *
     * <p>This is an HTML report generated from code coverage output from your build. The report can
     * be omitted by omitting {@link TestArgs#coverageResults}</p>
     *
     * <h5>JUnit report (REQUIRED)</h5>
     *
     * <p>This report feeds Jenkins the data about the current test run. It can be used to mark a build
     * as failed or unstable. The report location must be present in {@link TestArgs#junitOutput}</p>
     *
     * <h5>Cobertura Report</h5>
     *
     * <p>This report feeds Jenkins the data about the coverage results for the current test run. If
     * no Cobertura options are passed, then no coverage data will be collected. For more
     * information, see {@link TestArgs#cobertura}</p>
     *
     * <p>The test stage will execute by default if the current build result is greater than or
     * equal to {@link ResultEnum#UNSTABLE}. If a different status is passed, that will take
     * precedent.</p>
     *
     * <p>After the test is complete, the stage will continue to collect the JUnit Report and the Test
     * Results HTML Report. The stage will fail if either of those are missing. If specified, the
     * Code Coverage HTML Report and the Cobertura Report are then captured. The build will fail if
     * these reports are to be collected and were missing.</p>
     *
     * <p>Some tests may also require the use of the gnome-keyring. The stage can be configured to
     * unlock the keyring prior to the tests by passing {@link TestArgs#shouldUnlockKeyring} as true.</p>
     *
     * <h5>Stage Exceptions</h5>
     *
     * <p>The test stage can throw a {@link TestStageException} under any of the following
     * circumstances:</p>
     *
     * <ul>
     * <li>A test stage was created before a call to {@link #buildGeneric(Map)}</li>
     * <li>{@link TestArgs#testResults} was missing</li>
     * <li>Invalid options specified for {@link TestArgs#testResults}</li>
     * <li>{@link TestArgs#coverageResults} was provided but had an invalid format</li>
     * <li>{@link TestArgs#junitOutput} is missing.</li>
     * </ul>
     *
     * <p><b>Note:</b> This method was intended to be called {@code test} but had to be named
     * {@code testGeneric} due to the issues described in {@link Pipeline}.</p>
     *
     * @param arguments A map of arguments to be applied to the {@link TestArgs} used to define
     *                  the stage.
     */
    void testGeneric(Map arguments = [:]) {
        // Default the resultThreshold to unstable for tests,
        // if a custom value is passed then that will be used instead
        if (!arguments.resultThreshold) {
            arguments.resultThreshold = ResultEnum.UNSTABLE
        }

        TestArgs args = arguments

        TestStageException preSetupException

        if (args.stage) {
            preSetupException = new TestStageException("args.stage is an invalid option for testGeneric", args.name)
        }

        args.name = "Test: ${args.name}"
        args.stage = { String stageName ->
            // If there were any exceptions during the setup, throw them here so proper email notifications
            // can be sent.
            if (preSetupException) {
                throw preSetupException
            }

            if (!_didBuild) {
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

            try {
                args.operation(stageName)
            } catch (e) {
                steps.echo "Exception: ${e.getMessage()}"
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
                steps.cobertura(TestArgs.coberturaDefaults + args.cobertura)
            } else if (args.coverageResults) {
                steps.echo "WARNING: Cobertura file not detected, skipping"
            }

            _didTest = true
        }

        createStage(args)
    }

    /**
     * Validates that a test report has the required options.
     *
     * @param report The report to validate
     * @param reportName The name of the report being validated
     * @param stageName The name of the stage that is executing.
     *
     * @throws TestStageException when any of the report properties are invalid.
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
