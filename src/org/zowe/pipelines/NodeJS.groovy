package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

class NodeJS {
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

    def steps
    NodeJS(steps) { this.steps = steps }

    def setup() {
        steps.properties(steps.buildDiscarder(steps.logRotator(numToKeepStr: 5)))

        _createStage('checkout', {
            steps.checkout steps.scm
        })

        _createStage('setup', {
            steps.echo "Initializing Git Config"
            // @TODO as part of CD this should get filled out
        })

        _setupCalled = true

        // @TODO ADD STEP TO SEND EMAIL OUT HERE
    }

    // document later
    private void _createStage(
        String name,
        Closure stepContents,
        Map inputMap = [:]
    ) {
        def defaultMap = [isSkipable: false, timeout: 10, timeoutUnit: 'MINUTES', shouldSkip: { -> false }]
        def map = defaultMap << inputMap
        
        steps.stage(name) {
            steps.timeout(time: map.timeout, unit: map.timeoutUnit) {
                if (map.shouldSkip()) {
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
}
