package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils
import groovy.transform.ToString

public class NodeJS {
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

    def steps
    NodeJS(steps) { this.steps = steps }

    public void setup() {
        _setupCalled = true

        createStage(name: 'setup', stage: {
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
        createStage(name: 'checkout', stage: {
            steps.checkout steps.scm
        }, isSkipable: false)

        createStage(name: 'Check for CI Skip', stage: {
            steps.echo "@TODO"
        })

        createStage(name: 'Install Node Package Dependencies', stage: {
            steps.sh "npm install"
        }, isSkipable: false)
        // @TODO ADD STEP TO SEND EMAIL OUT HERE
    }

    // document later
    public void createStage(
        Map args
    ) {
        StageArgs args = new StageArgs(arguments)

        // def defaultMap = [isSkipable: true, timeout: 10, timeoutUnit: 'MINUTES', shouldSkip: { -> false }]
        // def map = defaultMap << inputMap
        
        steps.stage(args.name) {
            steps.timeout(time: args.timeoutVal, unit: args.timeoutUnit) {
                if (!_setupCalled) {
                    steps.error("Pipeline setup not complete, please execute setup() on the instantiated NodeJS class")
                } else if ((_shouldSkipRemainingSteps && args.isSkipable) || args.shouldSkip()) {
                    Utils.markStageSkippedForConditional(args.name);
                } else {
                    steps.echo "Executing stage ${args.name}"

                    if (args.isSkipable) { // @TODO FILL STRING OUT
                        steps.echo "Inform how to skip the step here"
                    }

                    stepContents()
                }
            }
        }
    }

    // @NamedVariant
    // public void buildStage(
    //     @NamedParam(required = true) String name,
    //     @NamedParam String test = "Hello"
    // ) {
    // Above doesn't work cause of groovy version
    public void buildStage(Map args) {
        // skipable only allow one of these, must happen before testing
        // allow custom build command, archive artifact

        // TestArgs test = new TestArgs(args)


        createStage("build", {
            steps.echo args.name
            steps.echo args.test
            steps.echo args.toString()
            steps.echo "FILL THIS OUT"  
        })
    }

    public void testStage() {
        // skipable, can have multiple, must happen before deploy after build
        // run in d-bus or not, allow custom test command, archive test results
        createStage("test", {
            steps.echo "FILL THIS OUT"  
        })
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
}
