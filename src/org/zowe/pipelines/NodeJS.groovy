package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

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

        createStage('setup', {
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
        })
        createStage('checkout', {
            steps.checkout steps.scm
        })

        createStage('Check for CI Skip', {
            steps.echo "@TODO"
        }, [isSkipable: true])
        // @TODO ADD STEP TO SEND EMAIL OUT HERE
    }

    // document later
    public void createStage(
        String name,
        Closure stepContents,
        Map inputMap = [:]
    ) {
        def defaultMap = [isSkipable: false, timeout: 10, timeoutUnit: 'MINUTES', shouldSkip: { -> false }]
        def map = defaultMap << inputMap
        
        steps.stage(name) {
            steps.timeout(time: map.timeout, unit: map.timeoutUnit) {
                if (!_setupCalled) {
                    steps.error("Pipeline setup not complete, please execute setup() on the instantiated NodeJS class")
                } else if ((_shouldSkipRemainingSteps && map.isSkipable) || map.shouldSkip()) {
                    Utils.markStageSkippedForConditional(name);
                } else {
                    steps.echo "Executing stage ${name}"

                    if (map.isSkipable) { // @TODO FILL STRING OUT
                        steps.echo "Inform how to skip the step here"
                    }

                    stepContents()
                }
            }
        }
    }

    public void buildStage() {
        // skipable only allow one of these, must happen before testing
        // allow custom build command, archive artifact

        createStage("build", {
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
