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

    public GitConfig gitConfig
    public RegistryConfig publishConfig    // Credentials for publish
    public RegistryConfig[] registryConfig // Credentials for download packages

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

    // Used to get the first failing stage
    public Stage getFirstFailingStage() {
        return _firstFailingStage
    }

    // Used to get information about stage execution to external users
    public Stage getStageInformation(String stageName) {
        return _stages.get(stageName)
    }


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
        }, isSkipable: false, timeout: [time: 5, unit: 'MINUTES']) // @TODO all timeouts should be configurable
    }

    // Separate class method in prep for other steps needing this functionality...cough...cough...deploy...cough
    private void _loginToRegistry(RegistryConfig registry) {
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
"""
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
                    // Skips the stage when called with a reason code
                    Closure skipStage = { reason -> 
                        steps.echo "Stage Skipped: \"${args.name}\" Reason: ${reason}"
                        Utils.markStageSkippedForConditional(args.name)
                    }

                    _closureWrapper(stage) {
                        // First check that setup was called first
                        if (!(_setupCalled && _firstStage.name.equals(_SETUP_STAGE_NAME))) {
                            throw new StageException(
                                "Pipeline setup not complete, please execute setup() on the instantiated NodeJS class",
                                args.name
                            )
                        } else if (!steps.currentBuild.resultIsBetterOrEqualTo(args.resultThreshold.value)) {
                            skipStage("${steps.currentBuild.currentResult} does not meet required threshold ${args.resultThreshold.value}")
                        } else if (stage.isSkippedByParam) {
                            skipStage("Skipped by build parameter")
                        } else if (_shouldSkipRemainingSteps) {
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

            if (!_firstFailingStage && steps.currentBuild.resultIsWorseOrEqualTo('UNSTABLE')) {
                _firstFailingStage = stage
                _firstFailingStage.exception = new StageException("Stage exited with a result of UNSTABLE or worse", stage.name)
            }
        }
    }

    public void buildStage(Map arguments = [:]) {
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

            steps.sh "tar -czvf ${BUILD_ARCHIVE_NAME} \"${args.output}\""
            steps.archiveArtifacts "${BUILD_ARCHIVE_NAME}"

            // @TODO should probably delete the archive from the workspace as soon
            // @TODO as it gets archived so that we can keep the git status clean

            _didBuild = true
        }

        createStage(args)
    }

    public void testStage(Map arguments = [:]) {
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
            def archiveLocation = "postBuildArchive"

            String[] archiveDirectories = ["/home/jenkins/.npm/_logs"] + archiveFolders

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
                    steps.echo "Unable to archive $directory, reason: ${e.message}"
                }
            }

            steps.archiveArtifacts allowEmptyArchive: true, artifacts: "$archiveLocation/**/*.*"

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
        if (_firstFailingStage != null && _firstFailingStage.exception != null) {
            bodyText += "<h3>Failure Details</h3>"
            bodyText += "<table>"
            bodyText += "<tr><td style=\"width: 150px\">First Failing Stage:</td><td><b>${_firstFailingStage.name}</b></td></tr>"
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

// Enumeration for the result
public enum ResultEnum {
    SUCCESS("SUCCESS"),
    NOT_BUILT("NOT_BUILT"),
    UNSTABLE("UNSTABLE"),
    FAILURE("FAILURE"),
    ABORTED("ABORTED");

    ResultEnum(String v) {
        value = v
    }
    private String value
    public String getValue() {
        return value
    }
}

// Specifies a registry to login to
class RegistryConfig {
    String url
    String email
    String credentialsId
}

class GitConfig {
    String user
    String email
    String credentialsId
}

class StageArgs { // @TODO Stage minimum build health (if build health is >= to this minimum, continue with the stage else skip)
    String name
    Closure stage
    boolean isSkipable = true
    StageTimeout timeout = [:]
    Closure shouldSkip = { -> false }
    Map<String, String> environment

    // The current health of the build must be this or better for the step to execute
    ResultEnum resultThreshold = ResultEnum.SUCCESS
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
