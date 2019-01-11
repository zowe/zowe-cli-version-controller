package org.zowe.pipelines

class NodeJS {
    def node
    NodeJS(node) { this.node = node }

    def setup(String test) {
        // steps.step('setup') {
        //     sh: "echo ${test}"
        // }
        node.stages {
            stage('setup') {
                sh: "echo ${test}"
            }
        }
    }
}
