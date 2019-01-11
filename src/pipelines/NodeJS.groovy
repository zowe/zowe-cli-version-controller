package org.zowe.pipelines

class NodeJS {
    static def setup(String test) {
        node() {
            step('setup') {
                sh: "echo ${test}"
            }
        }
    }
}
