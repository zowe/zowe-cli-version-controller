def call(String test) {
    node() {
        step('test step') {
            sh: "echo ${test}"
        }
    }
}