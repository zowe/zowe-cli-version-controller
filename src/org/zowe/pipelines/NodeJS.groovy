package org.zowe.pipelines

import org.jenkinsci.plugins.pipeline.modeldefinition.Utils

class NodeJS {
    def steps
    NodeJS(steps) { this.steps = steps }

    def setup(String test) {
        steps.stage('checkout') {
            steps.checkout steps.scm
        }

        steps.stage('setup') {
            steps.sh "echo ${test}"
        }
    }

    def setup2(String test) {
        steps.stage('setup2') {
            Utils.markStageSkippedForConditional('setup2')
        }
    }
}
