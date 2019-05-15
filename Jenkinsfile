/*
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License v2.0 which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-v20.html
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Copyright Contributors to the Zowe Project.
 */

node('ca-jenkins-agent') {
    def branch = ""
    
    try {
        branch = CHANGE_BRANCH
    } catch (MissingPropertyException e) {
        branch = BRANCH_NAME
    }
    
    def lib = library("shared-pipelines@$branch").org.zowe.pipelines.nodejs
    
    def nodejs = lib.NodeJSPipeline.new(this)

    nodejs.admins.add("zfernand0","markackert")

    nodejs.protectedBranches.addMap(
       name: "master"
    )

    nodejs.gitConfig = [
        email: 'zowe.robot@gmail.com',
        credentialsId: 'zowe-robot-github',
        githubAPIEndpoint: 'https://api.github.com/'
    ]

    nodejs.publishConfig = [
        email: nodejs.gitConfig.email,
        credentialsId: 'GizaArtifactory'
    ]

    nodejs.setup()

    nodejs.createStage(
        name: "Lint",
        stage: {
                sh "npm run lint"
        },
        timeout: [
            time: 2,
            unit: 'MINUTES'
        ]
    )

    nodejs.build(timeout: [
        time: 5,
        unit: 'MINUTES',
    ], operation:
       {
             sh "npm run build"
       })

    def UNIT_TEST_ROOT = "__tests__/__results__/unit"

    nodejs.test(
        name: "Unit",
        operation: {
            sh "npm run test:unit"
        },
        shouldUnlockKeyring: true,
        testResults: [dir: "${UNIT_TEST_ROOT}", files: "results.html", name: "Mock Project: Unit Test Report"],
        coverageResults: [dir: "${UNIT_TEST_ROOT}/coverage/lcov-report", files: "index.html", name: "Mock Project: Code Coverage Report"],
        junitOutput: "${UNIT_TEST_ROOT}/junit.xml",
        cobertura: [
            coberturaReportFile: "${UNIT_TEST_ROOT}/coverage/cobertura-coverage.xml"
        ]
    )
    nodejs.end()

}
