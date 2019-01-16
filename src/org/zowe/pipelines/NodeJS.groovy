package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import hudson.model.Result

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

    public String[] adminEmails = []

    // Key is branch name and value is npm tag name
    public Map protectedBranches = [master: 'latest']

    public Map gitConfig
    public Map publishConfig

    public String defaultBuildHistory = '5'
    public String protectedBranchBuildHistory = '20'

    private boolean _isProtectedBranch = false
    private boolean _shouldSkipRemainingSteps = false
    private boolean _didBuild = false

    // Map of all stages run
    private Map<String, Stage> _stages = [:]

    // Keeps track of the current stage
    private Stage _currentStage

    // Keeps track of the first failing stage
    private Stage _firstFailingStage

    // The build revision at the start of the build
    private String _buildRevision

    def steps

    NodeJS(steps) { this.steps = steps }

    public void setup() {
        // @TODO Fail if version was manually changed (allow for an override if we need to for some reason)
        // @TODO Allow for input to override control variables, takes an array of step names define in the current pipeline and allows for enable or disabling the step. There should also be skippable steps for ones that are automatically generated. For steps we might want to echo how it can be disabled as the first line of output in the step.
        // @TODO Keep each step in maybe a list so that we can see what ran and what didnt as well as the order, also add these to options for skiping
        try {
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

        } catch (e) {
            // If there was an exception thrown, the build failed
            // currentBuild.result = "FAILED"  TODO: what is the equivalent in scripted pipeline?
            throw e
        } finally {
            sendEmailNotification()
        }
    }

    // document later
    public void createStage(Map arguments) {
        // Parse arguments and initialize the stage
        StageArgs args = new StageArgs(arguments)
        Stage stageInfo = new Stage(name: args.name, order: _stages.size() + 1)

        // Add stage to map
        _stages.putAt(args.name, stageInfo)

        // Set the next stage from the current stage
        if (_currentStage) {
            _currentStage.next = stageInfo
        }

        // Set the new current stage to this stage
        _currentStage = stageInfo

        steps.stage(args.name) {
            try {
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
            } catch(e) {
                _firstFailingStage = stageInfo
                throw e
            } finally {
                stageInfo.endOfStepBuildStatus = steps.currentBuild.currentResult
            }
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
        createStage("test", {
            steps.echo "FILL THIS OUT"
        })
    }

    /**
     * Send an email notification about the result of the build to the appropriate users
     */
    public void sendEmailNotification() {
        steps.echo "Sending email notification..."
        steps.emailext(
                subject: "Build Email",
                to: "cc: " + adminEmails.join(","),
                body: "This is an email",
                mimeType: "text/html",
//                recipientProviders: [[$class: 'DevelopersRecipientProvider'],
//                                     [$class: 'UpstreamComitterRecipientProvider'],
//                                     [$class: 'CulpritsRecipientProvider'],
//                                     [$class: 'RequesterRecipientProvider']]
        )
    }

    // Shorthand for setting results
    public void setResult(Result result) {
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
    int order // The order of stage execution
    boolean wasExecuted = false
    String endOfStepBuildStatus // The result of the build at the end
    Stage next // The next stage
}
