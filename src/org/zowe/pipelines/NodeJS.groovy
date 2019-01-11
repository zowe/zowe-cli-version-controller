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
        })
    }

    def setup2(String test) {
        steps.stage('setup2') {
            Utils.markStageSkippedForConditional('setup2')
        }
    }

    private def _createStage(String name, boolean isSkipable, Closure stepContents) {
        steps.stage(name) {
            steps.echo "Executing stage ${name}"

            if (isSkipable) {
                steps.echo "Inform how to skip the step here"
            }

            stepContents()
        }
    }
}
