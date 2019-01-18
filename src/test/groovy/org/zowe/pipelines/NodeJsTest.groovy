package org.zowe.pipelines

import groovy.org.zowe.pipelines.NodeJS
import org.junit.Test

import static groovy.test.GroovyAssert.shouldFail

class NodeJsTest {
    @Test
    void indexOutOfBoundsAccess() {
        def numbers = [1, 2, 3, 4]
        System.out.println("hello?")
        def steps = new MockSteps()
        def myNode = new NodeJS(steps)
        System.out.println(myNode.notificationImages)
        shouldFail {
            numbers.get(99)
        }
    }
}

class MockSteps {
    public echo(String echoStr) {
        System.out.println(echoStr)
    }

    public error(String errStr) {
        throw new Exception(errStr)
    }
}
