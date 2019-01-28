package org.zowe.pipelines.nodejs

@Grab('org.apache.commons:commons-text:1.6')
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4

import org.zowe.pipelines.nodejs.models.*
import org.zowe.pipelines.nodejs.exceptions.*

import hudson.model.Result
import hudson.tasks.test.AbstractTestResultAction
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

// @TODO still needs to be documented
// @TODO include that the image to run on is controlled by the outer pipeline
class NodeJSRunner {
    /**
     * The name of the library output archived from the {@link #buildStage()} method.
     */
    static final String BUILD_ARCHIVE_NAME = "BuildArchive.tar.gz"

    /**
     * Text used for the CI SKIP commit.
     */
    static final String _CI_SKIP = "[ci skip]"

    /**
     * Shell command that gets the current git revision.
     */
    private static final String _GIT_REVISION_LOOKUP = "git log -n 1 --pretty=format:%h"

    /**
     * The name of the root setup stage.
     */
    private static final String _SETUP_STAGE_NAME = "Setup"

    /**
     * This is a list of administrator emails addresses that will receive emails when a build
     * happens on a protected branch.
     */
    String[] adminEmails = []

    /**
     * The number of historical builds kept for a non-protected branch.
     */
    String defaultBuildHistory = '5'

    // @FUTURE Only relevant for CD story
    /**
     * Git user configuration, add more documentation in future story
     */
    GitConfig gitConfig

    /**
     * Images embedded in notification emails depending on the status of the build.
     */
    Map<String, List<String>> notificationImages = [
        SUCCESS : [
            'https://i.imgur.com/ixx5WSq.png', /*happy seal*/
            'https://i.imgur.com/jiCQkYj.png'  /*happy puppy*/
        ],
        UNSTABLE: [
            'https://i.imgur.com/fV89ZD8.png'  /*not sure if*/
        ],
        FAILURE : [
            'https://i.imgur.com/iQ4DuYL.png'  /*this is fine fire */
        ]
    ]

    /**
     * The number of historical builds kept for a protected branch.
     */
    String protectedBranchBuildHistory = '20'

    // @FUTURE will be use heavily in the deploy story
    /*
     * A map of protected branches.
     *
     * The keys in the map represent the name of a protected branch.
     * The values represent the corresponding npm tag the branch is published to.
     */
    Map protectedBranches = [master: 'latest']

    // @FUTURE part of the deploy story
    /**
     * This is the connection information for the registry where code is published
     * to.
     */
    RegistryConfig publishConfig

    /**
     * An array of registry connection information information for each registry. These
     * logins will happen before the npm install in setup.
     */
    RegistryConfig[] registryConfig

    /**
     * The git commit revision of the build. This is determined at the beginning of
     * the build.
     */
    private String _buildRevision

    /**
     * A boolean that tracks if the build step was run. When false, the build still hasn't completed
     */
    private boolean _didBuild = false

    /**
     * Tracks if the current branch is protected.
     */
    private boolean _isProtectedBranch = false

    /**
     * Tracks if the setup method was called.
     */
    private boolean _setupCalled = false

    /**
     * Tracks if the remaining stages should be skipped.
     */
    private boolean _shouldSkipRemainingStages = false

    /**
     * The stages of the pipeline to execute. As stages are created, they are
     * added into this control class.
     */
    private PipelineStages _stages = new PipelineStages()

    /**
     * Reference to the groovy pipeline variable.
     *
     * @see #NodeJSRunner()
     */
    def steps

    /**
     * Options that are to be added to the build.
     */
    def buildOptions = []

    /**
     * Build parameters that will be defined to the build
     */
    def buildParameters = []

    /**
     * Constructs the class.
     *
     * When invoking from a Jenkins pipeline script, the NodeJSRunner must be passed
     * the current environment of the Jenkinsfile to have access to the steps.
     *
     * <h3>Example Setup:</h3>
     * def nodejs = new NodeJSRunner(this)
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    NodeJSRunner(steps) { this.steps = steps }

    /**
     * Creates a stage that will build a NodeJS package.
     *
     * Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link BuildArgs} class.
     *
     * Build: {@link BuildArgs#name}
     * ---------------------------------------------------------------------------------------------
     * Runs the build of your application. If {@link BuildArgs#buildOperation} is not provided, the
     * stage will default to executing `npm run build`.
     *
     * The build stage also ignores any {@link BuildArgs#resultThreshold} provided and only runs
     * on {@link ResultEnum#SUCCESS}.
     *
     * After the buildOperation is complete, the stage will continue to archive the contents of the
     * build into a tar file. The folder to archive is specified by arguments.output. In the future,
     * this function will run the npm pack command and archive that tar file instead.
     *
     * This stage will throw a {@link BuildStageException} if called more than once in your pipeline.
     * ---------------------------------------------------------------------------------------------
     *
     * @param arguments A map of arguments to be applied to the {@link BuildArgs} used to define
     *                  the stage.
     */
    void buildStage(Map arguments = [:]) {
        // Force build to only happen on success, this cannot be overridden
        arguments.resultThreshold = ResultEnum.SUCCESS

        BuildArgs args = arguments

        args.name = "Build: ${args.name}"
        args.stage = {
            if (_didBuild) {
                throw new BuildStageException("Only one build step is allowed per pipeline.", args.name)
            }

            // Either use a custom build script or the default npm run build
            if (args.buildOperation) {
                args.buildOperation()
            } else {
                steps.sh 'npm run build'
            }

            // @FUTURE In the deploy story, we should npm pack the build artifacts and archive that bundle instead for all builds.

            steps.sh "tar -czvf ${BUILD_ARCHIVE_NAME} \"${args.output}\""
            steps.archiveArtifacts "${BUILD_ARCHIVE_NAME}"
            steps.sh "rm -f ${BUILD_ARCHIVE_NAME}"

            _didBuild = true
        }

        createStage(args)
    }

    // @FUTURE NEED TO MAKE THIS A STANDALONE CLASS THAT NODE JS EXTENDS
    // @FUTURE TO REDUCE FILE SIZE
    /**
     * Creates a new stage to be run in the Jenkins pipeline.
     *
     * Stages are executed in the order that they are created. For more details on what arguments
     * can be sent into a stage, see the {@link StageArgs} class.
     *
     * @param args The arguments that define the stage.
     */
    void createStage(StageArgs args) {
        Stage stage = new Stage(args: args, name: args.name, order: _stages.size() + 1)

        _stages.add(stage)

        if (args.isSkipable) {
            // Add the option to the build, this will be called in setup
            buildParameters.push(
                    steps.booleanParam(
                            defaultValue: false,
                            description: "Setting this to true will skip the stage \"${args.name}\"",
                            name: getStageSkipOption(args.name)
                    )
            )
        }

        stage.execute = {
            steps.stage(args.name) {
                steps.timeout(time: args.timeout.time, unit: args.timeout.unit) {
                    // Skips the stage when called with a reason code
                    Closure skipStage = { reason ->
                        steps.echo "Stage Skipped: \"${args.name}\" Reason: ${reason}"
                        Utils.markStageSkippedForConditional(args.name)
                    }

                    // If the stage is skippable
                    if (stage.args.isSkipable) {
                        // Check if the stage was skipped by the build parameter
                        stage.isSkippedByParam = steps.params[getStageSkipOption(stage.name)]
                    }

                    _closureWrapper(stage) {
                        // First check that setup was called first
                        if (!(_setupCalled && _stages.firstStageToExecute.name.equals(_SETUP_STAGE_NAME))) {
                            throw new StageException(
                                    "Pipeline setup not complete, please execute setup() on the instantiated NodeJS class",
                                    args.name
                            )
                        } else if (!steps.currentBuild.resultIsBetterOrEqualTo(args.resultThreshold.value)) {
                            skipStage("${steps.currentBuild.currentResult} does not meet required threshold ${args.resultThreshold.value}")
                        } else if (stage.isSkippedByParam) {
                            skipStage("Skipped by build parameter")
                        } else if (!args.doesIgnoreSkipAll && _shouldSkipRemainingStages) {
                            // If doesIgnoreSkipAll is true then this check is ignored, all others are not though
                            skipStage("All remaining steps are skipped")
                        } else if (args.shouldSkip()) {
                            skipStage("Should skip function evaluated to true")
                        }
                        // Run the stage
                        else {
                            steps.echo "Executing stage ${args.name}"

                            stage.wasExecuted = true
                            if (args.isSkipable) {
                                steps.echo "This step can be skipped by setting the `${getStageSkipOption(args.name)}` option to true"
                            }

                            def environment = []

                            // Add items to the environment if needed
                            if (args.environment) {
                                args.environment.each { key, value -> environment.push("${key}=${value}") }
                            }

                            // Run the passed stage with the proper environment variables
                            steps.withEnv(environment) {
                                _closureWrapper(stage) {
                                    args.stage()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a new stage to be run in the Jenkins pipeline.
     *
     * @param arguments A map of arguments that can be instantiated to a {@link StageArgs} instance.
     *
     * @see #createStage(StageArgs)
     */
    void createStage(Map arguments) {
        // Parse arguments and initialize the stage
        StageArgs args = new StageArgs(arguments)

        // Call the overloaded method
        createStage(args)
    }

    /**
     * Gets the first failing stage within {@link #_stages}
     *
     * @return The first failing stage if one exists, null otherwise
     */
    Stage getFirstFailingStage() {
        return _stages.firstFailingStage
    }

    /**
     * Get a stage from the available stages by name.
     *
     * @param stageName The name of the stage object to get.
     *
     * @return The stage object for the requested stage.
     */
    Stage getStage(String stageName) {
        return _stages.getStage(stageName)
    }

    // @FUTURE a super class could define this method for setup and checkout and the nodejs
    // @FUTURE class can extend it to add the npm install stuff
    /**
     * Creates the pipeline setup stages.
     *
     * This method MUST be called before any other stages are created. If not called, your Jenkins
     * pipeline will fail. It is also recommended that any public properties of this class are set
     * prior to calling setup.
     *
     * The setup method creates 4 stages in your Jenkins pipeline using the {@link #createStage(Map)}
     * function.
     *
     * Setup:
     * ---------------------------------------------------------------------------------------------
     * Used internally to indicate that the NodeJSRunner properly set the pipeline up.
     * ---------------------------------------------------------------------------------------------
     *
     * Checkout:
     * ---------------------------------------------------------------------------------------------
     * Checks the git source out for the pipeline.
     * ---------------------------------------------------------------------------------------------
     *
     * Check for CI Skip:
     * ---------------------------------------------------------------------------------------------
     * Checks that the build commit doesn't contain the CI Skip indicator. If the pipeline finds
     * the skip commit, all remaining steps (except those explicitly set to ignore this condition)
     * will also be skipped. The build will also be marked as not built in this scenario.
     * ---------------------------------------------------------------------------------------------
     *
     * Install Node Package Dependencies:
     * ---------------------------------------------------------------------------------------------
     * This step will install all your package dependencies via `npm install`. Prior to install
     * the stage will login to any registries specified in the {@link #registryConfig} array. On
     * exit, the step will try to logout of the registries specified in {@link #registryConfig}.
     *
     * - Failure to login to a registry or install dependencies will result in a failed build.
     * - Failure to logout of a registry will not fail the build.
     * ---------------------------------------------------------------------------------------------
     */
    void setup() {
        // @TODO all timeouts should be configurable do as part of next story
        // @FUTURE Fail if version was manually changed (allow for an override if we need to for some reason) for DEPLOY
        _setupCalled = true

        createStage(name: _SETUP_STAGE_NAME, stage: {
            steps.echo "Setup was called first"

            if (_stages.firstFailingStage) {
                if (_stages.firstFailingStage.exception) {
                    throw _stages.firstFailingStage.exception
                } else {
                    throw new StageException("Setup found a failing stage but there was no associated exception.", _stages.firstFailingStage.name)
                }
            } else {
                steps.echo "No problems with preinitialization of pipeline :)"
            }
        }, isSkipable: false, timeout: [time: 10, unit: 'SECONDS'])

        createStage(name: 'Checkout', stage: {
            steps.checkout steps.scm
        }, isSkipable: false, timeout: [time: 1, unit: 'MINUTES'])

        createStage(name: 'Check for CI Skip', stage: {
            // We need to keep track of the current commit revision. This is to prevent the condition where
            // the build starts on master and another branch gets merged to master prior to version bump
            // commit taking place. If left unhandled, the version bump could be done on latest master branch
            // code which would already be ahead of this build.
            _buildRevision = steps.sh returnStatus: true, script: _GIT_REVISION_LOOKUP

            // This checks for the [ci skip] text. If found, the status code is 0
            def result = steps.sh returnStatus: true, script: 'git log -1 | grep \'.*\\[ci skip\\].*\''
            if (result == 0) {
                steps.echo "\"${_CI_SKIP}\" spotted in the git commit. Aborting."
                _shouldSkipRemainingStages = true
                setResult(Result.NOT_BUILT)
            }
        }, timeout: [time: 1, unit: 'MINUTES'])

        createStage(name: 'Install Node Package Dependencies', stage: {
            try {
                if (registryConfig) {
                    // Only one is allowed to use the default registry
                    // This will keep track of that
                    def didUseDefaultRegistry = false

                    steps.echo "Login to registries"

                    for (int i = 0; i < registryConfig.length; i++) {
                        def registry = registryConfig[i]

                        if (!registry.url) {
                            if (didUseDefaultRegistry) {
                                throw new NodeJSRunnerException("No registry specified for registryConfig[${i}] and was already logged into the default")
                            }
                            didUseDefaultRegistry = true
                        }

                        _loginToRegistry(registry)
                    }
                }

                steps.sh "npm install"
            } finally {
                // Always try to logout regardless of errors
                if (registryConfig) {
                    steps.echo "Logout of registries"

                    for (int i = 0; i < registryConfig.length; i++) {
                        _logoutOfRegistry(registryConfig[i])
                    }
                }
            }
        }, isSkipable: false, timeout: [time: 5, unit: 'MINUTES'])
    }

    /**
     * Creates a stage that will execute tests on a NodeJS.
     *
     * Calling this function will add the following stage to your Jenkins pipeline. Arguments passed
     * to this function will map to the {@link TestArgs} class.
     *
     * Test: {arguments.name}
     * ---------------------------------------------------------------------------------------------
     * Runs one of your application tests. If testOperation, the stage will execute `npm run test`
     * as the default operation. If the test operation throws an error, that error is ignored and
     * will be assumed to be caught in the junit processing. Some test functions may exit with a
     * non-zero return code on a test failure but may still capture junit output. In this scenario,
     * it is assumed that the junit report is either missing or contains failing tests. In the case
     * that it is missing, the build will fail on this report and relevant exceptions are printed.
     * If the junit report contains failing tests, the build will be marked as unstable and a report
     * of failing tests can be viewed.
     *
     * The following reports can be captured:
     *
     * -----------------------------------
     * Test Results HTML Report
     * -----------------------------------
     * This is an html report that contains the result of the build. The report must be defined to
     * the method in the {@link TestArgs#testResults} variable.
     *
     * -----------------------------------
     * Code Coverage HTML Report
     * -----------------------------------
     * This is an HTML report generated from code coverage output from your build. The report can
     * be omitted by omitting {@link TestArgs#coverageResults}
     *
     *
     *
     *
     *
     *
     *
     *
     *
     * The build stage also ignores any {@link BuildArgs#resultThreshold} provided and only runs
     * on {@link ResultEnum#SUCCESS}.
     *
     * After the buildOperation is complete, the stage will continue to archive the contents of the
     * build into a tar file. The folder to archive is specified by arguments.output. In the future,
     * this function will run the npm pack command and archive that tar file instead.
     *
     * This stage will throw a {@link BuildStageException} if called more than once in your pipeline.
     * ---------------------------------------------------------------------------------------------
     *
     * @param arguments A map of arguments to be applied to the {@link TestArgs} used to define
     *                  the stage.
     */
    void testStage(Map arguments = [:]) {
        // Default the resultThreshold to unstable for tests,
        // if a custom value is passed then that will be used instead
        if (!arguments.resultThreshold) {
            arguments.resultThreshold = ResultEnum.UNSTABLE
        }

        TestArgs args = arguments

        // @TODO one must happen before deploy
        args.name = "Test: ${args.name}"
        args.stage = {
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
                if (args.testOperation) {
                    args.testOperation()
                } else {
                    steps.sh "npm run test"
                }
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
            } else {
                steps.echo "Cobertura file not detected, skipping"
            }
        }

        createStage(args)
    }

    private void _closureWrapper(Stage stage, Closure closure) {
        try {
            closure()
        } catch (e) {
            _stages.firstFailingStage = stage

            setResult(Result.FAILURE)
            stage.exception = e

            throw e
        } finally {
            stage.endOfStepBuildStatus = steps.currentBuild.currentResult

            // Don't alert of the build status if the stage already has an exception
            if (!stage.exception && steps.currentBuild.resultIsWorseOrEqualTo('UNSTABLE')) {
                // Add the exception of the bad build status
                stage.exception = new StageException("Stage exited with a result of UNSTABLE or worse", stage.name)
                _stages.firstFailingStage = stage
            }
        }
    }

    private void _validateReportInfo(TestReport report, String reportName, String stageName) {
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

    // Npm logs will always be archived
    public void end(String[] archiveFolders = []) {
        createStage(name: "Log Archive", stage: {
            def archiveLocation = "postBuildArchive"

            String[] archiveDirectories = ["/home/jenkins/.npm/_logs"] + archiveFolders

            steps.echo "NOTE: If a directory was not able to be archived, the build will result in a success."
            steps.echo "NOTE: It works like this because it is easier to catch an archive error than logically determine when each specific archive directory is to be captured."
            steps.echo "NOTE: For example: if a log directory is only generated when there is an error but the build succeeds, the archive will fail."
            steps.echo "NOTE: It doesn't make sense for the build to fail in this scenario since the error archive failed because the build was a success."
            steps.sh "mkdir $archiveLocation"

            for (int i = 0; i < archiveDirectories.length; i++) {
                def directory = archiveDirectories[i]

                try {
                    if (directory.startsWith("/")) {
                        steps.sh "mkdir -p ./${archiveLocation}${directory}"

                        // It is an absolute path so try to copy everything into our work directory
                        steps.sh "cp -r $directory ./${archiveLocation}${directory}"
                    } else if (directory.startsWith("..")) {
                        throw new NodeJSRunnerException("Relative archives are not supported")
                    }
                } catch (e) {
                    steps.echo "Unable to archive $directory, reason: ${e.message}\n\n...Ignoring"
                }
            }

            steps.archiveArtifacts allowEmptyArchive: true, artifacts: "$archiveLocation/**/*.*"
        }, resultThreshold: ResultEnum.FAILURE, doesIgnoreSkipAll: true, isSkipable: false)

        try {
            // First setup the build properties
            def history = defaultBuildHistory;

            // Add protected branch to build options
            if (protectedBranches.containsKey(steps.BRANCH_NAME)) {
                _isProtectedBranch = true;
                history = protectedBranchBuildHistory
                buildOptions.push(steps.disableConcurrentBuilds())
            }

            // Add log rotator to build options
            buildOptions.push(steps.buildDiscarder(steps.logRotator(numToKeepStr: history)))

            // Add any parameters to the build here
            buildOptions.push(steps.parameters(buildParameters))

            steps.properties(buildOptions)

            // Execute the pipeline
            _stages.execute()
        } finally {
            sendEmailNotification(); // @FUTURE As part of the deploy story, extract email stuff into separate class
        }
    }

    private String getStageSkipOption(String name) {
        return "Skip Stage: ${name}"
    }

    // NonCPS informs jenkins to not save variable state that would resolve in a
    // java.io.NotSerializableException on the TestResults class
    @NonCPS
    private String _getTestSummary() {
        def testResultAction = steps.currentBuild.rawBuild.getAction(AbstractTestResultAction.class)
        def text = "<h3>Test Results</h3>"

        if (testResultAction != null) {
            def total = testResultAction.getTotalCount()
            def failed = testResultAction.getFailCount()
            def skipped = testResultAction.getSkipCount()

            // Create an overall summary
            text += "<p style=\"font-size: 16px;\">Passed: <span style=\"font-weight: bold; color: green\">${total - failed - skipped}</span>, "
            text += "Failed: <span style=\"font-weight: bold; color: ${failed == 0 ? "green" : "red"}\">${failed}</span>"

            if (skipped > 0) {
                text += ", Skipped: <span style=\"font-weight: bold; color: #027b77\">${skipped}</span>"
            }
            text += "</p>"

            // Now output failing results
            if (failed > 0) {
                // If there are more failures than this value, then we will only output
                // this number of failures to save on email size.
                def maxTestOutput = 5

                text += "<h4>Failing Tests</h4>"

                def codeStart = "<code style=\"white-space: pre-wrap; display: inline-block; vertical-align: top; margin-left: 10px; color: red\">"
                def failedTests = testResultAction.getFailedTests()
                def failedTestsListCount = failedTests.size() // Don't trust that failed == failedTests.size()

                // Loop through all tests or the first maxTestOutput, whichever is smallest
                for (int i = 0; i < maxTestOutput && i < failedTestsListCount; i++) {
                    def test = failedTests.get(i)

                    text += "<p style=\"margin-top: 5px; margin-bottom: 0px; border-bottom: solid 1px black; padding-bottom: 5px;"

                    if (i == 0) {
                        text += "border-top: solid 1px black; padding-top: 5px;"
                    }

                    text += "\"><b>Failed:</b> ${test.fullDisplayName}"

                    // Add error details
                    if (test.errorDetails) {
                        text += "<br/><b>Details:</b>${codeStart}${escapeHtml4(test.errorDetails)}</code>"
                    }

                    // Add stack trace
                    if (test.errorStackTrace) {
                        text += "<br/><b>Stacktrace:</b>${codeStart}${escapeHtml4(test.errorStackTrace)}</code>"
                    }

                    text += "</p>"
                }

                // Todo add elipsis if the total is greater than the max
                if (maxTestOutput < failedTestsListCount) {
                    text += "<p>...For the remaining failures, view the build output</p>"
                }
            }
        } else {
            text += "<p>No test results were found for this run.</p>"
        }

        return text
    }

    /**
     * Send an email notification about the result of the build to the appropriate users
     */
    public void sendEmailNotification() {
        steps.echo "Sending email notification..."
        def subject = "${steps.currentBuild.currentResult}: Job '${steps.env.JOB_NAME} [${steps.env.BUILD_NUMBER}]'"
        def bodyText = """
                        <h3>${steps.env.JOB_NAME}</h3>
                        <p>Branch: <b>${steps.BRANCH_NAME}</b></p>
                        <p><b>${steps.currentBuild.currentResult}</b></p>
                        <hr>
                        <p>Check console output at <a href="${steps.RUN_DISPLAY_URL}">${steps.env.JOB_NAME} [${
            steps.env.BUILD_NUMBER
        }]</a></p>
                        """

        // add an image reflecting the result
        if (notificationImages.containsKey(steps.currentBuild.currentResult) &&
                notificationImages[steps.currentBuild.currentResult].size() > 0) {
            def imageList = notificationImages[steps.currentBuild.currentResult];
            def imageIndex = Math.abs(new Random().nextInt() % imageList.size())
            bodyText += "<p><img src=\"" + imageList[imageIndex] + "\" width=\"500\"/></p>"
        }

        bodyText += _getTestSummary()

        // Add any details of an exception, if encountered
        if (_stages.firstFailingStage?.exception) { // Safe navigation is where the question mark comes from
            bodyText += "<h3>Failure Details</h3>"
            bodyText += "<table>"
            bodyText += "<tr><td style=\"width: 150px\">First Failing Stage:</td><td><b>${_stages.firstFailingStage.name}</b></td></tr>"
            bodyText += "<tr><td>Exception:</td><td>${_stages.firstFailingStage.exception.toString()}</td></tr>"
            bodyText += "<tr><td style=\"vertical-align: top\">Stack:</td>"
            bodyText += "<td style=\"color: red; display: block; max-height: 350px; max-width: 65vw; overflow: auto\">"
            bodyText += "<div style=\"width: max-content; font-family: monospace;\">"
            def stackTrace = _stages.firstFailingStage.exception.getStackTrace()

            for (int i = 0; i < stackTrace.length; i++) {
                bodyText += "at ${stackTrace[i]}<br/>"
            }

            bodyText += "</div></td></tr>";
            bodyText += "</table>"
        }

        List<String> ccList = new ArrayList<String>();
        if (_isProtectedBranch) {
            // only CC administrators if we are on a protected branch
            for (String email : adminEmails) {
                ccList.add("cc: " + email);
            }
        }
        try {
            steps.echo bodyText // log out the exception too
            // send the email
            steps.emailext(
                    subject: subject,
                    to: ccList.join(","),
                    body: bodyText,
                    mimeType: "text/html",
                    recipientProviders: [[$class: 'DevelopersRecipientProvider'],
                                         [$class: 'UpstreamComitterRecipientProvider'],
                                         [$class: 'CulpritsRecipientProvider'],
                                         [$class: 'RequesterRecipientProvider']]
            )
        }
        catch (emailException) {
            steps.echo "Exception encountered while attempting to send email!"
            steps.echo emailException.toString();
            steps.echo emailException.getStackTrace().join("\n")
        }

    }


    // Shorthand for setting results
    public void setResult(Result result) {
        steps.currentBuild.result = result
    }

    /**
     *
     * @param registry
     * @throws NodeJSRunnerException
     */
    private void _loginToRegistry(RegistryConfig registry) throws NodeJSRunnerException {
        if (!registry.email) {
            throw new NodeJSRunnerException("Missing email address for registry: ${registry.url ? registry.url : "default"}")
        }
        if (!registry.credentialsId) {
            throw new NodeJSRunnerException("Missing credentials for registry: ${registry.url ? registry.url : "default"}")
        }

        if (!registry.url) {
            steps.echo "Attempting to login to the default registry"
        } else {
            steps.echo "Attempting to login to the ${registry.url} registry"
        }

        // Bad formatting but this is probably the cleanest way to do the expect script
        def expectCommand = """/usr/bin/expect <<EOD
set timeout 60
#npm login command, add whatever command-line args are necessary
spawn npm login ${registry.url ? "--registry ${registry.url}" : ""}
match_max 100000

expect "Username"
send "\$EXPECT_USERNAME\\r"

expect "Password"
send "\$EXPECT_PASSWORD\\r"

expect "Email"
send "\$EXPECT_EMAIL\\r"

expect {
   timeout      exit 1
   eof
}
"""
        // Echo the command that was run
        steps.echo expectCommand

        steps.withCredentials([
                steps.usernamePassword(
                        credentialsId: registry.credentialsId,
                        usernameVariable: 'EXPECT_USERNAME',
                        passwordVariable: 'EXPECT_PASSWORD'
                )
        ]) {
            steps.withEnv(["EXPECT_EMAIL=${registry.email}"]) {
                steps.sh expectCommand
            }
        }
    }

    private void _logoutOfRegistry(RegistryConfig registry) {
        if (!registry.url) {
            steps.echo "Attempting to logout of the default registry"
        } else {
            steps.echo "Attempting to logout of the ${registry.url} registry"
        }

        try {
            // If the logout fails, don't blow up. Coded this way because a failed
            // logout doesn't mean we've failed. It also doesn't stop any other
            // logouts that might need to be done.
            steps.sh "npm logout ${registry.url ? "--registry registry.url" : ""}"
        } catch (e) {
            steps.echo "Failed logout but will continue"
        }
    }
}
