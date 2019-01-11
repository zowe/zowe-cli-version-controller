package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

class NodeJS {
    def steps
    NodeJS(steps) { this.steps = steps }

    def setup(String test) {
        _createStage('checkout', false, {
            steps.checkout steps.scm
        })

        _createStage('setup', false, {
            steps.sh "echo ${test}"
        }, shouldSkip: { true })
    }

    // Define this function later
    private def _createStage(
        String name,
        boolean isSkipable,
        Closure stepContents,
        int timeout = 10,
        int timeoutUnit = 'MINUTES',
        Closure shouldSkip = { false }
    ) {
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
