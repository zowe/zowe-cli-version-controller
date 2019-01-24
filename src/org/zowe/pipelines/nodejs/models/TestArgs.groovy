/**
 * Class representing configuring for a test stage of a jenkins pipeline
 */
class TestArgs extends StageArgs {
    Closure testOperation

    boolean shouldUnlockKeyring = false // Should the keyring be unlocked for the test

    TestReport testResults     // Required
    TestReport coverageResults // Optional

    String junitOutput // Required

    // Need cobertura stuff as well
    Map cobertura

    public static final Map coberturaDefaults = [
            autoUpdateStability       : true,
            classCoverageTargets      : '85, 80, 75',
            conditionalCoverageTargets: '70, 65, 60',
            failUnhealthy             : false,
            failUnstable              : false,
            fileCoverageTargets       : '100, 95, 90',
            lineCoverageTargets       : '80, 70, 50',
            maxNumberOfBuilds         : 20,
            methodCoverageTargets     : '80, 70, 50',
            onlyStable                : false,
            sourceEncoding            : 'ASCII',
            zoomCoverageChart         : false
    ]
}
