@Library('shared-pipelines@automated_Tests')
import org.zowe.pipelines.nodejs.NodeJSRunner

node('ca-jenkins-agent') {
    def nodejs = new NodeJSRunner(this)

    nodejs.adminEmails = [
        "christopher.wright@broadcom.com",
        "fernando.rijocedeno@broadcom.com",
        "michael.bauer2@broadcom.com",
        "mark.ackert@broadcom.com",
        "daniel.kelosky@broadcom.com"
    ]

    nodejs.protectedBranches = [
        master: 'daily'
    ]

    nodejs.gitConfig = [
        user: 'zowe-robot',
        email: 'zowe.robot@gmail.com',
        credentialId: 'zowe-robot-github'
    ]

    nodejs.publishConfig = [
        email: nodejs.gitConfig.email,
        credentialId: 'GizaArtifactory'
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

    nodejs.buildStage(timeout: [
        time: 5,
        unit: 'MINUTES',
    ],  buildOperation:
       {
             sh "npm run build"
       })

    def UNIT_TEST_ROOT = "__tests__/__results__/unit"

    nodejs.testStage(
        name: "Unit",
        testOperation: {
            sh "npm run test:unit"
        },
        shouldUnlockKeyring: true,
        testResults: [dir: "${UNIT_TEST_ROOT}/html", files: "index.html", name: "Mock Project: Unit Test Report"],
        coverageResults: [dir: "${UNIT_TEST_ROOT}/coverage/lcov-report", files: "index.html", name: "Mock Project: Code Coverage Report"],
        junitOutput: "${UNIT_TEST_ROOT}/junit.xml",
        cobertura: [
            coberturaReportFile: "${UNIT_TEST_ROOT}/coverage/cobertura-coverage.xml"
        ]
    )
    nodejs.end()

}
