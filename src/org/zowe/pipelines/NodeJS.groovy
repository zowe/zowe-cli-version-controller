package org.zowe.pipelines

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
            steps.sh "ls -al"
            steps.sh "pwd"
        }
    }
}
