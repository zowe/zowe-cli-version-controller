package org.zowe.pipelines

import groovy.org.zowe.pipelines.NodeJS
import hudson.model.Result
import org.junit.Test

/**
 * Unit tests for the NodeJS class
 */
class NodeJsTest {
    /**
     * Test instantiating a NodeJS object
     */
    @Test
    void instantiationTest() {
        def steps = new MockSteps()
        def myNode = new NodeJS(steps)
        myNode.setup();
    }

    /**
     * Test creating a new stage in the build
     */
    @Test
    void createStageTest() {
        def steps = new MockSteps()
        def myNode = new NodeJS(steps)
        myNode.setup()

        myNode.createStage(name: "lint", stage: {
            steps.sh "npm run lint"
        }, isSkipable: false)

        myNode.end()
    }

}

/**
 * Mocked class for "steps" Jenkins context
 */
class MockSteps {
    public echo(String echoStr) {
        System.out.println(echoStr)
    }

    public error(String errStr) {
        throw new Exception(errStr)
    }

    public checkout() {

    }

    public scm() {

    }

    public sh() {

    }

    public emailext(LinkedHashMap emailParams) {

    }

    public stage(String name, Closure closure) {

    }

    public timeout() {

    }

    public booleanParam(LinkedHashMap param) {

    }

    public logRotator(LinkedHashMap params) {

    }

    public buildDiscarder(LinkedHashMap params) {

    }

    public parameters(ArrayList params) {

    }

    public properties(ArrayList props) {

    }

    public String FAILED_TESTS = "These are the failed tests"
    public Map params = ["Skip Stage: lint": "hello"]
    public Map currentBuild = [currentResult: "NOT_STARTED", result: Result.SUCCESS]

    public Map env = [BUILD_NUMBER: "1", JOB_NAME: "Zowe Cli Version Controller"]

    public String BRANCH_NAME = "Mocked_Branch"

    public String RUN_DISPLAY_URL = "https://www.google.com"
}
