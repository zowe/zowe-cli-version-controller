package org.zowe.pipelines.base

import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException
import org.zowe.pipelines.base.models.*
import org.zowe.pipelines.base.exceptions.*

import java.util.concurrent.TimeUnit

@Grab('org.apache.commons:commons-text:1.6')
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4

import hudson.tasks.test.AbstractTestResultAction
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import com.cloudbees.groovy.cps.NonCPS

/**
 * This class represents a basic Jenkins pipeline. Use the methods of this class to add stages to
 * your pipeline.
 *
 * <h5>Basic Usage</h5>
 *
 * <pre>
 * {@literal @}Library('fill this out according to your setup') import org.zowe.pipelines.base.Pipeline
 *
 * import org.zowe.pipelines.base.models.ProtectedBranch
 *
 * node('pipeline-node') {
 *   Pipeline pipeline = new Pipeline(this)
 *
 *   // Set your config up before calling setup
 *   pipeline.adminEmails = [
 *       "email1@example.com",
 *       "email2@example.com"
 *   ]
 *
 *   pipeline.protectedBranches.add(new ProtectedBranch(name: "master"))
 *
 *   // MUST BE CALLED FIRST
 *   pipeline.setupBase()
 *
 *   // Create a stage in your pipeline
 *   pipeline.createStage(name: 'Some Pipeline Stage', stage: {
 *       echo "This is my stage"
 *   })
 *
 *   // MUST BE CALLED LAST
 *   pipeline.endBase()
 * }
 * </pre>
 *
 * <p>In the above example, the stages will run on a node labeled {@code 'pipeline-node'}. You
 * must define the node where your pipeline will execute.</p>
 *
 * <p>Stages are not executed until the {@link #endBase} method is called. This means that you can't
 * rely on stages being executed as soon as they are defined. If you need logic to help determine if
 * a stage should be executed, you must use the proper options allowed by {@link StageArgs}.</p>
 *
 * <p><b>Note:</b> Due to issues with how Jenkins loads a shared pipeline library, you might notice
 * that some methods that were meant to be overridden by subclasses are just named differently. This
 * is due to how the CPS Jenkins wrapper modifies classes and subclasses. See below for the full
 * explanation of the problem.</p>
 *
 * <ul>
 *     <li>The superclass implements a method of the signature {@code setup()}</li>
 *     <li>The subclass implements a method of the signature {@code setup()}. Which is the same as the
 *     superclass, so under normal OOP the method would be overridden.</li>
 *     <li>In the subclass's implementation of {@code setup()}, it calls {@code super.setup()}.<ul>
 *             <li>In normal Groovy OOP, this would be fine and the superclass method will be called
 *             properly.</li>
 *             <li>In Jenkins, calling {@code super.setup()} actually calls the subclass method.
 *             this results in a stack overflow error, since the method is just calling itself until
 *             the method stack is completely used up.</li>
 *         </ul>
 *     </li>
 * </ul>
 *
 * <p>I believe this is due to how Jenkins does inheritance. It looks like Jenkins is just taking
 * all the methods available across the superclasses of a class and adding them as a base
 * definition to the class itself. When there's a conflict, it seems that Jenkins just takes the
 * method from the lowest class in the hierarchy and refers all calls to that method no matter what.
 * This theory stems from the fact that if you call a superclass method that accesses a superclass
 * private variable, Jenkins complains that the variable doesn't exist. If you change that variable
 * or method to protected or public, Jenkins doesn't complain anymore and operation acts as normal.
 * </p>
 *
 * <p>This issue seems to signify that they may never fix this problem.
 * {@link https://issues.jenkins-ci.org/browse/JENKINS-47355?jql=text%20~%20%22inherit%20class%22}</p>
 */
class Pipeline {
    /**
     * The name of the root setup stage.
     */
    protected static final String _SETUP_STAGE_NAME = "Setup"

    /**
     * This is a list of administrator emails addresses that will receive emails when a build
     * happens on a protected branch.
     */
    final PipelineAdmins admins = new PipelineAdmins()

    /**
     * The number of historical builds kept for a non-protected branch.
     */
    String defaultBuildHistory = '5'

    /**
     * Images embedded in notification emails depending on the status of the build.
     */
    Map<String, List<String>> notificationImages = [
            SUCCESS : [
                    'https://i.imgur.com/ixx5WSq.png', /*happy seal*/
                    'https://i.imgur.com/jiCQkYj.png'  /*happy puppy*/
            ],
            UNSTABLE: [
                    'https://i.imgur.com/fV89ZD8.png',  /*not sure if*/
                    'https://media.giphy.com/media/rmRUASq4WujsY/giphy.gif' /*f1 tires fly off*/
            ],
            FAILURE : [
                    'https://i.imgur.com/iQ4DuYL.png',  /*this is fine fire */
                    'https://media.giphy.com/media/3X0nMYG46US2c/giphy.gif' /*terminator sink into lava*/
            ],
            ABORTED : [
                    'https://i.imgur.com/Zq0iBJK.jpg' /* surprised pikachu */
            ]
    ]

    /**
     * The number of historical builds kept for a protected branch.
     */
    String protectedBranchBuildHistory = '20'

    // @FUTURE will be use heavily in the deploy story
    /**
     * A map of protected branches.
     *
     * <p>Any branches that are specified as protected will also have concurrent builds disabled. This
     * is to prevent issues with publishing.</p>
     */
    ProtectedBranches<ProtectedBranch> protectedBranches = new ProtectedBranches<ProtectedBranch>(ProtectedBranch.class)

    /**
     * Tracks if the current branch is protected.
     */
    protected boolean _isProtectedBranch = false

    /**
     * Tracks if the setup method was called.
     */
    protected boolean _setupCalled = false

    /**
     * Tracks if the remaining stages should be skipped.
     */
    protected boolean _shouldSkipRemainingStages = false

    /**
     * The stages of the pipeline to execute. As stages are created, they are
     * added into this control class.
     */
    protected final Stages _stages = new Stages()

    /**
     * Reference to the groovy pipeline variable.
     *
     * @see #Pipeline(def)
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
     * <p>When invoking from a Jenkins pipeline script, the Pipeline must be passed
     * the current environment of the Jenkinsfile to have access to the steps.</p>
     *
     * <h5>Example Setup:</h5>
     * <pre>
     * def pipeline = new Pipeline(this)
     * </pre>
     *
     * @param steps The workflow steps object provided by the Jenkins pipeline
     */
    Pipeline(steps) {
        this.steps = steps
    }

    // @FUTURE allow easy way for create stage to specify build parameters
    /**
     * Creates a new stage to be run in the Jenkins pipeline.
     *
     * <p>Stages are executed in the order that they are created. For more details on what arguments
     * can be sent into a stage, see the {@link StageArgs} class.</p>
     *
     * <p>Stages can also encounter various conditions that will cause them to skip. The following
     * skip search order is used when determining if a stage should be skipped.</p>
     *
     * <ol>
     * <li>If the {@link StageArgs#resultThreshold} value is greater than the current result, the
     * stage will be skipped. There is no override for this operation.</li>
     * <li>If the stage is skipable and the stage skip build option was passed, the stage will
     * be skipped.</li>
     * <li>If the remaining pipeline stages are to be skipped, then this stage will be skipped. This
     * can be overridden if the stage has set {@link StageArgs#doesIgnoreSkipAll} to true.</li>
     * <li>Finally, if the call to {@link StageArgs#shouldExecute} returns false, the stage will be
     * skipped.</li>
     * </ol>
     *
     * <p>If the stage is not skipped after executing the above checks, the stage will continue to
     * its execute phase.</p>
     *
     * @param args The arguments that define the stage.
     */
    final void createStage(StageArgs args) {
        Stage stage = new Stage(args: args, name: args.name, order: _stages.size() + 1)

        _stages.add(stage)

        if (args.isSkippable) {
            // Add the option to the build, this will be called in setup
            buildParameters.push(
                    steps.booleanParam(
                            defaultValue: false,
                            description: "Setting this to true will skip the stage \"${args.name}\"",
                            name: _getStageSkipOption(stage)
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
                    if (stage.args.isSkippable) {
                        // Check if the stage was skipped by the build parameter
                        stage.isSkippedByParam = steps.params[_getStageSkipOption(stage)]
                    }

                    _closureWrapper(stage) {
                        // First check that setup was called first
                        if (!(_setupCalled && _stages.firstStageToExecute.name == _SETUP_STAGE_NAME)) {
                            throw new StageException(
                                    "Pipeline setup not complete, please execute setup() on the instantiated BasePipeline class",
                                    args.name
                            )
                        } else if (!steps.currentBuild.resultIsBetterOrEqualTo(args.resultThreshold.value)) {
                            skipStage("${steps.currentBuild.currentResult} does not meet required threshold ${args.resultThreshold.value}")
                        } else if (stage.isSkippedByParam) {
                            skipStage("Skipped by build parameter")
                        } else if (!args.doesIgnoreSkipAll && _shouldSkipRemainingStages) {
                            // If doesIgnoreSkipAll is true then this check is ignored, all others are not though
                            skipStage("All remaining steps are skipped")
                        } else if (!args.shouldExecute()) {
                            skipStage("Stage was not executed due to shouldExecute being false")
                        }
                        // Run the stage
                        else {
                            steps.echo "Executing stage ${args.name}"

                            stage.wasExecuted = true
                            if (args.isSkippable) {
                                steps.echo "This step can be skipped by setting the `${_getStageSkipOption(stage)}` option to true"
                            }

                            def environment = []

                            // Add items to the environment if needed
                            if (args.environment) {
                                args.environment.each { key, value -> environment.push("${key}=${value}") }
                            }

                            // Run the passed stage with the proper environment variables
                            steps.withEnv(environment) {
                                _closureWrapper(stage) {
                                    args.stage(stage.name)
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
    final void createStage(Map arguments) {
        // Parse arguments and initialize the stage
        StageArgs args = new StageArgs(arguments)

        // Call the overloaded method
        createStage(args)
    }

    // @TODO MERGE THE TWO END ITEMS

    /**
     * End the pipeline and collect specified log files.
     *
     * <p>The end method adds the following operations to the end of the pipeline to the pipeline:</p>
     *
     * <h5>Capture Logs</h5>
     *
     * <p>Any folders specified in archiveFolders will be archived. If a folder is not available, the
     * archive will fail. In this scenario, the build will note the copy step as failed but will
     * not modify the current build result. This allows you to list directories to archive that only
     * appear under certain scenarios without the worry that they will affect the result of your
     * build when missing.</p>
     *
     * <p>These operations are put into the alwaysAction closure that is passed to the {@link #endBase(Closure)}
     * class method.</p>
     *
     * <p><b>Note:</b> This method was intended to be called {@code end} but had to be named
     * {@code endBase} due to the issues described in {@link Pipeline}.</p>
     *
     * @param archiveFolders An array of folders to archive. If a specific folder doesn't exist, the
     *                       build will ignore it and will not modify the current build result. See
     *                       the notes in the log for the reasoning. If a folder in this array
     *                       starts with a {@literal `/`}, the stage will copy the folder into a temp directory
     *                       inside the project (retaining the folder structure). This is due to
     *                       the fact that folders outside the workspace cannot be archived by
     *                       Jenkins. The leading {@literal `/`} should be used for any logs that you wish to
     *                       capture that are outside the workspace. Also if the directory starts
     *                       with a {@literal ../}, the stage will abort access to that folder. This is because
     *                       Jenkins cannot archive files outside the workspace.
     */

    /**
     * Call to inform the runner that no more stages are to be added and execution can begin.
     *
     * <p>The end method MUST be the last method called as part of your pipeline. The end method is
     * responsible for executing all the stages previously created after setting the required build
     * options and possible stage parameters. Failure to call this method will prevent your pipeline
     * stages from executing.</p>
     *
     * <p>Prior to executing the stages, various build options are set. Some of these options include
     * the build history and stage skip parameters. After this is done, the method will execute
     * all of the created stages in the order they were defined.</p>
     *
     * <p>After stage execution, an email will be sent out to those that made the commit. If the build
     * failed or returned to normal, all committers since the last successful build will also
     * receive the email. Finally if this build is on a protected branch, all emails listed in the
     * {@link #adminEmails} list will also receive a status email.</p>
     *
     * <p><b>Note:</b> This method was intended to be called {@code end} but had to be named
     * {@code endBase} due to the issues described in {@link Pipeline}.</p>
     *
     * @param alwaysAction This is a closure that is always executed at the end of the pipeline prior
     *                     prior to emails being sent out.
     */
    final void endBase(EndArgs options) {
        try {
            // First setup the build properties
            def history = defaultBuildHistory

            // Add protected branch to build options
            if (protectedBranches.isProtected(steps.BRANCH_NAME)) {
                _isProtectedBranch = true
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
            steps.echo "------------------------------------------------------------------------------------------------" +
                       "------------------------------------------------------------------------------------------------"
            steps.echo "POST BUILD ACTIONS"

            if (options.always) {
                options.always()
            }

            // Gather the log folders here
            _gatherLogs(options.archiveFolders)
            _sendEmailNotification()
        }
    }

    final void endBase(Map options) {
        endBase(new EndArgs(options))
    }

    /**
     * Gets the first failing stage within {@link #_stages}
     *
     * @return The first failing stage if one exists, null otherwise
     */
    final Stage getFirstFailingStage() {
        return _stages.firstFailingStage
    }

    /**
     * Get a stage from the available stages by name.
     *
     * @param stageName The name of the stage object to get.
     *
     * @return The stage object for the requested stage.
     */
    final Stage getStage(String stageName) {
        return _stages.getStage(stageName)
    }

    final void sendHtmlEmail(Map options) {
        sendHtmlEmail(new EmailOptions(options))
    }

    final void sendHtmlEmail(EmailOptions options) {
        def subject = "[$options.subjectTag] Job '${steps.env.JOB_NAME} [${steps.env.BUILD_NUMBER}]'"

        // send the email
        steps.emailext(
                subject: subject,
                to: options.to,
                body: options.body,
                mimeType: "text/html",
                recipientProviders: options.addProviders ? [[$class: 'DevelopersRecipientProvider'],
                                     [$class: 'UpstreamComitterRecipientProvider'],
                                     [$class: 'CulpritsRecipientProvider'],
                                     [$class: 'RequesterRecipientProvider']] : []
        )

    }

    /**
     * Set the build result
     * @param result The new result for the build.
     */
    final void setResult(ResultEnum result) {
        steps.currentBuild.result = result.value
    }

    /**
     * Creates the pipeline setup stages.
     *
     * <p>This method MUST be called before any other stages are created. If not called, your Jenkins
     * pipeline will fail. It is also recommended that any public properties of this class are set
     * prior to calling setup.</p>
     *
     * <p>When extending the Pipeline class, this method must be called in any overridden setup
     * methods. Failure to do so will result in the pipeline indicating that setup was never called.
     * </p>
     *
     * <p>The setup method creates 2 stages in your Jenkins pipeline using the {@link #createStage(Map)}
     * function.</p>
     *
     * <h4>Setup</h4>
     *
     * <p>Used internally to indicate that the Pipeline has been properly setup.</p>
     *
     * <h4>Checkout</h4>
     *
     * <p>Checks the source out for the pipeline.</p>
     *
     * <p><b>Note:</b> This method was intended to be called {@code setup} but had to be named
     * {@code setupBase} due to the issues described in {@link Pipeline}.</p>
     */
    void setupBase() {
        // @TODO all timeouts should be configurable do as part of next story
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
        }, isSkippable: false, timeout: [time: 10, unit: TimeUnit.SECONDS])

        createStage(name: 'Checkout', stage: {
            steps.checkout steps.scm
        }, isSkippable: false, timeout: [time: 1, unit: TimeUnit.MINUTES])
    }

    /**
     * Wraps a closure function in a try catch.
     *
     * <p>Used internally by {@link #createStage(StageArgs)} to handle errors thrown by timeouts and
     * stage executions.</p>
     *
     * @param stage The stage that is currently executing
     * @param closure The closure function to execute
     */
    protected final void _closureWrapper(Stage stage, Closure closure) {
        try {
            closure()
        } catch (e) {
            _stages.firstFailingStage = stage

            setResult(ResultEnum.FAILURE)
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

    protected final void _gatherLogs(String[] archiveFolders) {
        if (archiveFolders && archiveFolders.length > 0) {
            def archiveLocation = "postBuildArchive"

            steps.echo "NOTE: If a directory was not able to be archived, the build will result in a success."
            steps.echo "NOTE: It works like this because it is easier to catch an archive error than logically determine when each specific archive directory is to be captured."
            steps.echo "NOTE: For example: if a log directory is only generated when there is an error but the build succeeds, the archive will fail."
            steps.echo "NOTE: It doesn't make sense for the build to fail in this scenario since the error archive failed because the build was a success."
            steps.sh "mkdir $archiveLocation"

            for (int i = 0; i < archiveFolders.length; i++) {
                def directory = archiveFolders[i]

                try {
                    if (directory.startsWith("/")) {
                        steps.sh "mkdir -p ./${archiveLocation}${directory}"

                        // It is an absolute path so try to copy everything into our work directory
                        // always exit with 0 return code so the ui doesn't look broken
                        steps.sh "cp -r $directory ./${archiveLocation}${directory} || exit 0"
                    } else if (directory.startsWith("..")) {
                        throw new PipelineException("Relative archives are not supported")
                    }
                } catch (e) {
                    steps.echo "Unable to archive $directory, reason: ${e.message}\n\n...Ignoring"
                }
            }

            steps.archiveArtifacts allowEmptyArchive: true, artifacts: "$archiveLocation/*" + "*/*.*"// The weird concat because groovydoc blew up here
        }
    }

    /**
     * Gets the stage skip parameter name.
     *
     * @param stage The stage to skip.
     * @return The name of the skip stage parameter.
     */
    protected static String _getStageSkipOption(Stage stage) {
        return "Skip Stage: ${stage.name}"
    }

    // NonCPS informs jenkins to not save variable state that would resolve in a
    // java.io.NotSerializableException on the TestResults class
    /**
     * Gets a test summary string.
     *
     * <p>This method was created using {@literal @NonCPS} because some of the operations performed cannot be
     * serialized. The {@literal @NonCPS} annotation tells jenkins to not save the variable state of this
     * function on shutdown. Failure to run in this mode causes a java.io.NotSerializableException
     * in this method.</p>
     *
     * @return An HTML string of test results to add to the email.
     */
    @NonCPS
    protected final String _getTestSummary() {
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
    protected void _sendEmailNotification() {
        // @TODO If not built don't send an email
        // @TODO Get list of commits that were built

        String buildStatus = "${steps.currentBuild.currentResult}"
        String emailText = buildStatus

        if (firstFailingStage?.exception?.class == FlowInterruptedException.class) {
            buildStatus = "${((FlowInterruptedException) firstFailingStage.exception).result}"

            if (buildStatus == "ABORTED") {
                emailText = "Aborted by ${((FlowInterruptedException) firstFailingStage.exception).causes[0]?.user}"
            } else {
                emailText = buildStatus
            }
        }

        steps.echo "Sending email notification..."
        def subject = buildStatus
        def bodyText = """
                        <h3>${steps.env.JOB_NAME}</h3>
                        <p>Branch: <b>${steps.BRANCH_NAME}</b></p>
                        <p><b>$emailText</b></p>
                        <hr>
                        <p>Check console output at <a href="${steps.RUN_DISPLAY_URL}">${steps.env.JOB_NAME} [${
            steps.env.BUILD_NUMBER
        }]</a></p>
                        """

        // add an image reflecting the result
        if (notificationImages.containsKey(buildStatus) &&
                notificationImages[buildStatus].size() > 0) {
            def imageList = notificationImages[buildStatus]
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

            bodyText += "</div></td></tr>"
            bodyText += "</table>"
        }

        try {
            steps.echo bodyText

            // send the email
            sendHtmlEmail(
                    subjectTag: subject,
                    body: bodyText,
                    to: _isProtectedBranch ? admins.getCCList() : ""
            )
        }
        catch (emailException) {
            steps.echo "Exception encountered while attempting to send email!"
            steps.echo emailException.toString()
            steps.echo emailException.getStackTrace().join("\n")
        }
    }
}
