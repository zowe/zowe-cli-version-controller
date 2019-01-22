package org.zowe.pipelines.nodejs

@Grab('org.apache.commons:commons-text:1.6')
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4

import hudson.model.Result
import hudson.tasks.test.AbstractTestResultAction
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

// @TODO enforce some sort of ordering
// @TODO add way to archive logs in a folder, probably need to copy to workspace then archive
public class NodeJSRunner {
    public static final String BUILD_ARCHIVE_NAME = "BuildArchive.tar.gz"

    // Look up the git version
    private static final String _GIT_REVISION_LOOKUP = "git log -n 1 --pretty=format:%h"

    // CI Skip text
    private static final String _CI_SKIP = "[ci skip]"

    private static final String _SETUP_STAGE_NAME = "Setup"

    /**
     * Store if the setup method was called
     */
    private boolean _setupCalled = false
    private boolean _setupStageCalled = false

    /**
     * Any exception that has been encountered during the execution of the pipeline
     *
     */
    private Exception encounteredException = null

    public String[] adminEmails = []

    // Key is branch name and value is npm tag name
    public Map protectedBranches = [master: 'latest']

    /**
     * Images embedded in notification emails depending on the status of the build
     */
    public Map<String, List<String>> notificationImages = [SUCCESS : ['https://i.imgur.com/ixx5WSq.png', /*happy seal*/
                                                                      'https://i.imgur.com/jiCQkYj.png' /* happy puppy*/],
                                                           UNSTABLE: ['https://i.imgur.com/fV89ZD8.png' /* not sure if*/],
                                                           FAILURE : ['https://i.imgur.com/iQ4DuYL.png' /* this is fine fire */
                                                           ]]

    public Map gitConfig
    public Map publishConfig

    public String defaultBuildHistory = '5'
    public String protectedBranchBuildHistory = '20'

    private boolean _isProtectedBranch = false
    private boolean _shouldSkipRemainingSteps = false
    private boolean _didBuild = false

    def buildOptions = []
    def buildParameters = [] // Build parameter definitions

    // Map of all stages run
    private Map<String, Stage> _stages = [:]

    // Keeps track of the current stage
    private Stage _currentStage

    // The first stage to execute
    private Stage _firstStage

    // Keeps track of the first failing stage
    private Stage _firstFailingStage

    // The build revision at the start of the build
    private String _buildRevision

    def steps

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


    NodeJSRunner(steps) { this.steps = steps }

    public void setup() {
        // @TODO Fail if version was manually changed (allow for an override if we need to for some reason)
        _setupCalled = true

        createStage(name: _SETUP_STAGE_NAME, stage: {
            steps.echo "Setup was called first"

            if (_firstFailingStage) {
                if (_firstFailingStage.exception) {
                    throw _firstFailingStage.exception
                } else {
                    throw new StageException("Setup found a failing stage but there was no associated exception.", _firstFailingStage.name)
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
                _shouldSkipRemainingSteps = true
                setResult(Result.NOT_BUILT)
            }
        }, timeout: [time: 1, unit: 'MINUTES'])

        createStage(name: 'Install Node Package Dependencies', stage: {
            steps.sh "npm install"
        }, isSkipable: false, timeout: [time: 5, unit: 'MINUTES'])

    }

    // Takes instantiated args and runs a stage
    public void createStage(StageArgs args) {
        Stage stage = new Stage(args: args, name: args.name, order: _stages.size() + 1)
        
        if (_stages.containsKey(stage.name)) {
            if (_firstStage == null) {
                // This is a condition that indicates that our logic is most likely broken
                throw new StageException("First stage was not set but stages already had values in the map", stage.name)
            } else if (!_firstFailingStage){
                // The first stage should be setup, othewise a stage exception will be
                // thrown before we get into here. So in setup, we should create the exception
                // to be thrown later.
                _firstFailingStage = _firstStage
                _firstFailingStage.exception = new StageException("Duplicate stage name: \"${stage.name}\"", _firstFailingStage.name)
            }
        } else {
            // Add stage to map
            _stages.putAt(args.name, stage)
        }

        // Set the next stage from the current stage
        if (_currentStage) {
            _currentStage.next = stage
        }

        // If the first stage hasn't been created yet, set it here
        if (!_firstStage) {
            _firstStage = stage
        }

        // Set the new current stage to this stage
        _currentStage = stage

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
                    _closureWrapper(stage) {
                        // First check that setup was called first
                        if (!(_setupCalled && _firstStage.name.equals(_SETUP_STAGE_NAME))) {
                            throw new StageException(
                                "Pipeline setup not complete, please execute setup() on the instantiated NodeJS class",
                                args.name
                            )
                        }
                        // Next check to see if the stage should be skipped
                        else if (stage.isSkippedByParam || _shouldSkipRemainingSteps || args.shouldSkip()) {
                            // @TODO echo out the condition that caused the skip
                            Utils.markStageSkippedForConditional(args.name);
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

    // document later
    // accept stage as a map to instantiate
    public void createStage(Map arguments) {
        // Parse arguments and initialize the stage
        StageArgs args = new StageArgs(arguments)

        // Call the overloaded method
        createStage(args)
    }

    private void _closureWrapper(Stage stage, Closure closure) {
        try {
            closure()
        } catch (e) {
            if (!_firstFailingStage) {
                // If there was an exception thrown, the build failed. Save the exception we encountered
                _firstFailingStage = stage
            }
            setResult(Result.FAILURE)
            stage.exception = e // @TODO place this as part of the stage class

            throw e
        } finally {
            stage.endOfStepBuildStatus = steps.currentBuild.currentResult
        }
    }

    // @NamedVariant
    // public void buildStage(
    //     @NamedParam(required = true) String name,
    //     @NamedParam String test = "Hello"
    // ) {
    // Above doesn't work cause of groovy version
    public void buildStage(Map arguments = [:]) {
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

            steps.sh "tar -czvf ${BUILD_ARCHIVE_NAME} \"${args.output}\""
            steps.archiveArtifacts "${BUILD_ARCHIVE_NAME}"

            // @TODO should probably delete the archive from the workspace as soon
            // @TODO as it gets archived so that we can keep the git status clean

            _didBuild = true
        }

        createStage(args)
    }

    public void testStage(Map arguments = [:]) {
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

    public void end() {
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

            Stage stage = _firstStage

            while (stage) {
                // Get the parameters for the stage
                if (stage.args.isSkipable) {
                    stage.isSkippedByParam = steps.params[getStageSkipOption(stage.name)]
                }

                stage.execute()
                stage = stage.next
            }
        } finally {
            sendEmailNotification();
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
            text += "<p>Passed: <span style=\"font-weight: bold; color: green\">${total - failed - skipped}</span>, "
            text += "Failed: <span style=\"font-weight: bold; color: ${failed == 0 ? "green" : "red"}\">${failed}</span>"
            
            if (skipped > 0) {
                text += ", Skipped: <span style=\"font-weight: bold; color: #027b77\">${skipped}</span>"
            }
            text += "</p>"

            if (failed > 0) {
                def maxTestOutput = 20

                text += "<h4>Failing Tests</h4>"

                def failedTests = testResultAction.getFailedTests()
                def failedTestsListCount = failedTests.size() // Don't trust that failed == failedTests.size()

                // Loop through all tests or the first 20, whichever is smallest
                for (int i = 0; i < maxTestOutput && i < failedTestsListCount; i++) {
                    def test = failedTests.get(i)

                    text += "<p style=\"border-top: solid 1px black\"><b>Failed:</b> ${test.fullDisplayName}"
                    
                    if (test.errorDetails) {
                        text += "<br/><b>Details:</b><pre>${test.errorDetails}</pre>"
                    }

                    if (test.errorStackTrace) {
                        text += "<br/><b>Stacktrace:</b><pre>${escapeHtml4(test.errorStackTrace)}</pre>"
                    }

                    text += "</p>"
                }

                // Todo add elipsis if the total is greater than the max
                if (maxTestOutput < failedTestsListCount) {
                    text += "<p>...</p>"
                    text += "<p>For the remaining failures, view the build output</p>"
                }

            }

            // Now output the failing results if there are any, truncate after 20
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
        if (_firstFailingStage != null && _firstFailingStage.exception != null) {
            bodyText += "<h3>Failure Details</h3>"
            bodyText += "<table style=\"font-size: 16px\">"
            bodyText += "<tr><td style=\"width: 150px\">Failing Stage:</td><td><b>${_firstFailingStage.name}</b></td></tr>"
            bodyText += "<tr><td>Exception:</td><td>${_firstFailingStage.exception.toString()}</td></tr>"
            bodyText += "<tr><td style=\"vertical-align: top\">Stack:</td>"
            bodyText += "<td style=\"color: red; display: block; max-height: 350px; max-width: 65vw; overflow: auto\">"
            bodyText += "<div style=\"width: max-content; font-family: monospace;\">"
            def stackTrace = _firstFailingStage.exception.getStackTrace()

            for (int i = 0; i < stackTrace.length; i++) {
                bodyText += "at ${stackTrace[i]}<br/>"
            }

            bodyText += "</div></td></tr>";
            bodyText += "</table>"
        }

        List<String> ccList = new ArrayList<String>();
        if (protectedBranches.containsKey(steps.BRANCH_NAME)) {
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
}

// @TODO split out classes

////////////////////////////////////////////////////////////////////////////////
////////////////////// DATA FORMATS ////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////
class StageArgs { // @TODO Stage minimum build health (if build health is >= to this minimum, continue with the stage else skip)
    String name
    Closure stage
    boolean isSkipable = true
    StageTimeout timeout = [:]
    Closure shouldSkip = { -> false }
    Map<String, String> environment
}

class StageTimeout {
    int time = 10
    String unit = 'MINUTES'
}

class BuildArgs extends StageArgs {
    String output = "./lib/"
    String name = "Source"
    Closure buildOperation
}

class TestArgs extends StageArgs {
    Closure testOperation

    boolean shouldUnlockKeyring = false // Should the keyring be unlocked for the test

    TestReport testResults     // Required
    TestReport coverageResults // Optional

    String junitOutput // Required

    // Need cobertura stuff as well
    Map cobertura

    public static final Map coberturaDefaults = [
        autoUpdateStability       : true,
        classCoverageTargets      : '85, 80, 75',
        conditionalCoverageTargets: '70, 65, 60',
        failUnhealthy             : false,
        failUnstable              : false,
        fileCoverageTargets       : '100, 95, 90',
        lineCoverageTargets       : '80, 70, 50',
        maxNumberOfBuilds         : 20,
        methodCoverageTargets     : '80, 70, 50',
        onlyStable                : false,
        sourceEncoding            : 'ASCII',
        zoomCoverageChart         : false
    ]
}

class TestReport {
    String dir
    String files
    String name = "Test Report"
}

class Stage {
    String name
    int order // The order of stage execution
    boolean isSkippedByParam = false
    boolean wasExecuted = false
    String endOfStepBuildStatus // The result of the build at the end
    Stage next // The next stage
    StageArgs args
    Closure execute // The closure to execute for the stage
    /**
     * any exception encountered during the stage
     */
    Exception exception
}

////////////////////////////////////////////////////////////////////////////////
//////////////////////////// EXCEPTIONS ////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////

class NodeJSRunnerException extends Exception {
    NodeJSRunnerException(String message) {
        super(message)
    }
}

class StageException extends NodeJSRunnerException {
    String stageName

    StageException(String message, String stageName) {
        super("${message} (stage = \"${stageName}\")")

        this.stageName = stageName
    }
}

class TestStageException extends StageException {
    TestStageException(String message, String stageName) {
        super(message, stageName)
    }
}

class BuildStageException extends StageException {
    BuildStageException(String message, String stageName) {
        super(message, stageName)
    }
}
