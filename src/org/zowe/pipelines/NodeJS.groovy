package org.zowe.pipelines

class NodeJS {
    def steps
    NodeJS(steps) { this.steps = steps }

    def setup(String test) {
        // steps.step('setup') {
        //     sh: "echo ${test}"
        // }
        steps.node {
            stages {
                stage('setup') {
                    sh: "echo ${test}"
                }
            }
        }
    }
}