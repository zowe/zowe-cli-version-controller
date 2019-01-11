package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

class NodeJS {
    def steps
    NodeJS(steps) { this.steps = steps }

    def setup(String test) {
        _createStage('checkout', {
            steps.checkout steps.scm
        })

        _createStage('setup', {
            steps.sh "echo ${test}"
        }, [shouldSkip: { -> true }])
    }

    // Define this function later
    private void _createStage(
        String name,
        Closure stepContents,
        Map inputMap = [:]
    ) {
        def defaultMap = [isSkipable: false, timeout: 10, timeoutUnit: 'MINUTES', shouldSkip: { -> false }]
        def map = defaultMap << inputMap
        
        steps.stage(name) {
            if (shouldSkip()) {
                Utils.markStageSkippedForConditional(name);
            } else {
                steps.echo "Executing stage ${name}"

                if (isSkipable) { // @TODO FILL STRING OUT
                    steps.echo "Inform how to skip the step here"
                }

                stepContents()
            }
        }
    }
}
