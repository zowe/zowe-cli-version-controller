package org.zowe.pipelines

import hudson.model.Result
import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

public class NodeJS {
    public static final String BUILD_ARCHIVE_NAME = "BuildArchive.tar.gz"

    // Look up the git version
    private static final String _GIT_REVISION_LOOKUP = "git log -n 1 --pretty=format:%h"

    // CI Skip text
    private static final String _CI_SKIP = "[ci skip]"

    /**
     * Store if the setup method was called
     */
    private boolean _setupCalled = false

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

    private Map<String, Stage> _stages = [:]

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


    NodeJS(steps) { this.steps = steps }

    public void setup() {
        _setupCalled = true

        createStage(name: 'Setup', stage: {
            steps.echo "Setting up build configuration"

            def opts = [];
            def history = defaultBuildHistory;

            if (protectedBranches.containsKey(steps.BRANCH_NAME)) {
                _isProtectedBranch = true;
                history = protectedBranchBuildHistory
                opts.push(steps.disableConcurrentBuilds())
            }

            opts.push(steps.buildDiscarder(steps.logRotator(numToKeepStr: history)))
            steps.properties(opts)
        }, isSkipable: false)

        createStage(name: 'Checkout', stage: {
            steps.checkout steps.scm
        }, isSkipable: false)

        createStage(name: 'Check for CI Skip', stage: {
            // We need to keep track of the current commit revision. This is to prevent the condition where
            // the build starts on master and another branch gets merged to master prior to version bump
            // commit taking place. If left unhandled, the version bump could be done on latest master branch
            // code which would already be ahead of this build.
            _buildRevision = steps.sh returnStatus: true, script: NodeJS._GIT_REVISION_LOOKUP

            // This checks for the [ci skip] text. If found, the status code is 0
            def result = steps.sh returnStatus: true, script: 'git log -1 | grep \'.*\\[ci skip\\].*\''
            if (result == 0) {
                steps.echo "\"${NodeJS._CI_SKIP}\" spotted in the git commit. Aborting."
                _shouldSkipRemainingSteps = true
                setResult(Result.NOT_BUILT)
            }
        })

        createStage(name: 'Install Node Package Dependencies', stage: {
            steps.sh "npm install"
        }, isSkipable: false)

    }

    // document later
    public void createStage(Map arguments) {

        StageArgs args = new StageArgs(arguments)
        Stage stageInfo = new Stage(name: args.name, order: _stages.size() + 1)

        // @TODO add stage to map
        try {
            steps.stage(args.name) {
                steps.timeout(time: args.timeoutVal, unit: args.timeoutUnit) {
                    if (!_setupCalled) {
                        steps.error("Pipeline setup not complete, please execute setup() on the instantiated NodeJS class")
                    } else if (_shouldSkipRemainingSteps || args.shouldSkip()) {
                        Utils.markStageSkippedForConditional(args.name);
                    } else {
                        steps.echo "Executing stage ${args.name}"

                        stageInfo.wasExecuted = true
                        if (args.isSkipable) { // @TODO FILL STRING OUT
                            steps.echo "Inform how to skip the step here"
                        }

                        def environment = []

                        // Add items to the environment if needed
                        if (args.environment) {
                            args.environment.each { key, value -> environment.push("${key}=${value}") }
                        }

                        // Run the passed stage with the proper environment variables
                        steps.withEnv(environment) {
                            args.stage()
                        }
                    }
                }
            }
        } catch (e) {
            // If there was an exception thrown, the build failed. Save the exception we encountered
            steps.currentBuild.result = BUILD_FAILURE
            encounteredException = e
        } finally {
            stageInfo.endOfStepBuildStatus = steps.currentBuild.currentResult
        }

    }

    // @NamedVariant
    // public void buildStage(
    //     @NamedParam(required = true) String name,
    //     @NamedParam String test = "Hello"
    // ) {
    // Above doesn't work cause of groovy version
    public void buildStage(Map arguments = [:]) {
        // @TODO must happen before testing
        BuildArgs args = new BuildArgs(arguments)

        createStage(arguments + [name: "Build: ${args.name}", stage: {
            if (_didBuild) {
                steps.error "Only one build step is allowed per pipeline."
            }

            // Either use a custom build script or the default npm run build
            if (args.buildOperation) {
                args.buildOperation()
            } else {
                steps.sh 'npm run build'
            }

            steps.sh "tar -czvf ${NodeJS.BUILD_ARCHIVE_NAME} \"${args.output}\""
            steps.archiveArtifacts "${NodeJS.BUILD_ARCHIVE_NAME}"

            // @TODO should probably delete the archive from the workspace as soon
            // @TODO as it gets archived so that we can keep the git status clean

            _didBuild = true
        }])
    }

    public void testStage() {
        // @TODO skipable
        // @TODO can have multiple
        // @TODO must happen before deploy after build
        // @TODO  run in d-bus or not
        // @TODO allow custom test command
        // @TODO archive test results
        createStage(name: "test", stage: {
            steps.echo "FILL THIS OUT"
        })
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
            bodyText += "<p><img src=\"" + imageList[imageIndex] + "\" width=\"500\"></p>"
        }

        // Add any details of an exception, if encountered
        if (encounteredException != null) {
            bodyText += "<p>The following exception was encountered during the build: </p>"
            bodyText += "<p>" + encounteredException.toString() + "</p>";
            bodyText += "<p>" + encounteredException.getStackTrace().join("</p><p>") + "</p>";
            steps.echo bodyText // log out the exception too
        }

        List<String> ccList = new ArrayList<String>();
        if (protectedBranches.containsKey(steps.BRANCH_NAME)) {
            // only CC administrators if we are on a protected branch
            for (String email : adminEmails) {
                ccList.add("cc: " + email);
            }
        }
        try {
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

    /**
     * Call this after you have created all of your stages and done all of the work of your pipeline.
     *
     * This performs any tear-down steps for the pipeline and send an email notification
     */
    public void end() {
        sendEmailNotification();
    }

    // Shorthand for setting results
    public void setResult(Result result) { // @TODO need to make this an internal enum
        steps.currentBuild.result = result
    }
}

// @ToString(includeFields = true, includeNames = true)
class StageArgs {
    String name
    Closure stage
    boolean isSkipable = true
    int timeoutVal = 10
    String timeoutUnit = 'MINUTES'
    Closure shouldSkip = { -> false }
    Map<String, String> environment
}

class BuildArgs extends StageArgs {
    String output = "./lib/"
    String name = "Source"
    Closure buildOperation
}

class Stage {
    String name
    int order
    boolean wasExecuted = false
    String endOfStepBuildStatus
}
