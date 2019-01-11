package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

class NodeJS {
    /**
     * Store if the setup method was called
     */
    private boolean _setupCalled = false

    def steps
    NodeJS(steps) { this.steps = steps }

    def setup() {
        _createStage('checkout', {
            steps.checkout steps.scm
        })

        _createStage('setup', {
            steps.sh "echo SETUP HAPPENING"
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
