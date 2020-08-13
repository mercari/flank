package ftl.json

import com.google.api.services.testing.model.Environment
import com.google.api.services.testing.model.GoogleCloudStorage
import com.google.api.services.testing.model.ResultStorage
import com.google.api.services.testing.model.TestExecution
import com.google.api.services.testing.model.TestMatrix
import com.google.api.services.testing.model.ToolResultsExecution
import com.google.api.services.testing.model.ToolResultsStep
import com.google.common.truth.Truth.assertThat
import ftl.config.Device
import ftl.gc.GcAndroidDevice
import ftl.test.util.FlankTestRunner
import ftl.util.MatrixState.FINISHED
import ftl.util.MatrixState.INVALID
import ftl.util.MatrixState.PENDING
import ftl.util.webLink
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(FlankTestRunner::class)
class SavedMatrixTest {

    companion object {
        const val projectId = "1"
        const val historyId = "2"
        const val executionId = "1"
        const val testMatrixId = "123"

        // use -1 step id to get a failure outcome from the mock server
        fun createStepExecution(stepId: Int, deviceModel: String = "shamu", executionId: Int? = null): TestExecution {
            val toolResultsStep = ToolResultsStep()
            toolResultsStep.projectId = projectId
            toolResultsStep.historyId = historyId
            toolResultsStep.executionId = executionId?.toString() ?: stepId.toString()
            toolResultsStep.stepId = stepId.toString()

            val testExecution = TestExecution()
            testExecution.toolResultsStep = toolResultsStep

            val androidDevice = GcAndroidDevice.build(Device(deviceModel, "23"))
            testExecution.environment = Environment().setAndroidDevice(androidDevice)

            return testExecution
        }

        private const val mockFileName = "mockFileName"
        private const val mockBucket = "mockBucket"
        private val mockGcsPath = "$mockBucket/$mockFileName"

        fun createResultsStorage() = ResultStorage().apply {
            googleCloudStorage = GoogleCloudStorage().apply {
                gcsPath = mockGcsPath
            }
            toolResultsExecution = ToolResultsExecution().also {
                it.historyId = historyId
                it.projectId = projectId
                it.executionId = executionId
            }
        }

        fun testMatrix() = TestMatrix().also {
            it.projectId = projectId
            it.testMatrixId = testMatrixId
        }
    }

    @Test
    fun `savedMatrix failureOutcome`() {
        // Verify that if we have two executions: failure then success
        // the SavedMatrix outcome is correctly recorded as failure
        val testExecutions = listOf(
            createStepExecution(-1),
            createStepExecution(1)
        )

        val matrixId = "123"
        val matrixState = FINISHED
        val testMatrix = testMatrix()
        testMatrix.testMatrixId = matrixId
        testMatrix.state = matrixState
        testMatrix.resultStorage = createResultsStorage().apply {
            toolResultsExecution.executionId = "-1"
        }
        testMatrix.testExecutions = testExecutions

        val savedMatrix = SavedMatrix(testMatrix)
        assertThat(savedMatrix.outcome).isEqualTo("failure")

        // assert other properties
        assertThat(savedMatrix.matrixId).isEqualTo(matrixId)
        assertThat(savedMatrix.state).isEqualTo(matrixState)
        assertThat(savedMatrix.gcsPath).isEqualTo(mockGcsPath)
        assertThat(savedMatrix.webLink).isEqualTo("https://console.firebase.google.com/project/1/testlab/histories/2/matrices/-1")
        assertThat(savedMatrix.downloaded).isFalse()
        assertThat(savedMatrix.billableVirtualMinutes).isEqualTo(1)
        assertThat(savedMatrix.billablePhysicalMinutes).isEqualTo(1)
        assertThat(savedMatrix.gcsPathWithoutRootBucket).isEqualTo(mockFileName)
        assertThat(savedMatrix.gcsRootBucket).isEqualTo(mockBucket)
        assertThat(savedMatrix.outcomeDetails).isNotEmpty()
    }

    @Test
    fun `savedMatrix skippedOutcome`() {
        // Verify that if we have two executions: skipped
        // the SavedMatrix outcome is correctly recorded as skipped
        val testExecutions = listOf(
            createStepExecution(-3)
        )

        val matrixId = "123"
        val matrixState = FINISHED
        val testMatrix = testMatrix()
        testMatrix.testMatrixId = matrixId
        testMatrix.state = matrixState
        testMatrix.resultStorage = createResultsStorage().apply {
            toolResultsExecution.executionId = "-3"
        }
        testMatrix.testExecutions = testExecutions

        val savedMatrix = SavedMatrix(testMatrix)
        assertThat(savedMatrix.outcome).isEqualTo("skipped")

        // assert other properties
        assertThat(savedMatrix.matrixId).isEqualTo(matrixId)
        assertThat(savedMatrix.state).isEqualTo(matrixState)
        assertThat(savedMatrix.gcsPath).isEqualTo(mockGcsPath)
        assertThat(savedMatrix.webLink).isEqualTo("https://console.firebase.google.com/project/1/testlab/histories/2/matrices/-3/executions/-3")
        assertThat(savedMatrix.downloaded).isFalse()
        assertThat(savedMatrix.billableVirtualMinutes).isEqualTo(1)
        assertThat(savedMatrix.billablePhysicalMinutes).isEqualTo(1)
        assertThat(savedMatrix.gcsPathWithoutRootBucket).isEqualTo(mockFileName)
        assertThat(savedMatrix.gcsRootBucket).isEqualTo(mockBucket)
        assertThat(savedMatrix.outcomeDetails).isNotEmpty()
    }

    @Test
    fun `savedMatrix update`() {
        val testExecutions = listOf(
            createStepExecution(1, "shamu"),
            createStepExecution(1, "NexusLowRes")
        )

        val matrixId = "123"
        val testMatrix = testMatrix()
        testMatrix.testMatrixId = matrixId
        testMatrix.state = PENDING
        testMatrix.resultStorage = createResultsStorage()
        testMatrix.testExecutions = testExecutions

        val savedMatrix = SavedMatrix(testMatrix)
        savedMatrix.update(testMatrix)
        testMatrix.state = FINISHED
        testMatrix.webLink()
        savedMatrix.update(testMatrix)
    }

    @Test
    fun `savedMatrix on finish should calculate cost when state != ERROR`() {
        val testExecutions = listOf(
            createStepExecution(1, "shamu"),
            createStepExecution(1, "NexusLowRes")
        )
        val testMatrix = testMatrix()
        testMatrix.projectId = projectId
        testMatrix.testMatrixId = "123"
        testMatrix.state = PENDING
        testMatrix.resultStorage = createResultsStorage()
        testMatrix.testExecutions = testExecutions

        val savedMatrix = SavedMatrix(testMatrix)
        savedMatrix.update(testMatrix)

        testMatrix.state = FINISHED
        testMatrix.webLink()
        savedMatrix.update(testMatrix)
        assertEquals(1, savedMatrix.billableVirtualMinutes)
        assertEquals(1, savedMatrix.billablePhysicalMinutes)
    }

    @Test
    fun `savedMatrix should have outcome and outcome details properly filled when state is INVALID`() {
        val expectedOutcome = "---"
        val expectedOutcomeDetails = "Matrix is invalid"
        val testMatrix = testMatrix()
        testMatrix.testMatrixId = "123"
        testMatrix.state = PENDING
        testMatrix.resultStorage = createResultsStorage()

        val savedMatrix = SavedMatrix(testMatrix)
        savedMatrix.update(testMatrix)

        testMatrix.state = INVALID
        savedMatrix.update(testMatrix)
        assertEquals(expectedOutcome, savedMatrix.outcome)
        assertEquals(expectedOutcomeDetails, savedMatrix.outcomeDetails)
        assertEquals(INVALID, savedMatrix.state)
    }

    @Test
    fun `savedMatrix should have failed outcome when at least one test is failed`() {
        val expectedOutcome = "failure"
        val successStepExecution = createStepExecution(1) // success
        val failedStepExecution = createStepExecution(-1) // failure
        val flakyStepExecution = createStepExecution(-4) // flaky
        // https://github.com/Flank/flank/issues/914
        // This test covers edge case where the last test execution to check is flaky
        // based on different outcome from step (failed) and execution (success)
        // step.outcome != execution.outcome => means flaky
        val flakyOutcomeComparedStepExecution = createStepExecution(stepId = -1, executionId = 1) // flaky

        // below order in the list matters!
        val executions = listOf(
            flakyStepExecution,
            successStepExecution,
            failedStepExecution,
            flakyOutcomeComparedStepExecution
        )

        val testMatrix = testMatrix().apply {
            testMatrixId = "123"
            state = FINISHED
            resultStorage = createResultsStorage().apply {
                toolResultsExecution.executionId = "-1"
            }
            testExecutions = executions
        }

        val savedMatrix = SavedMatrix(testMatrix)

        assertEquals(
            "Does not return failed outcome when last execution is flaky",
            expectedOutcome,
            savedMatrix.outcome
        )
    }

    @Ignore("Should be used to verify https://github.com/Flank/flank/issues/918 fix")
    @Test
    fun `savedMatrix should have flaky outcome when at least one test is flaky`() {
        val expectedOutcome = "flaky"
        val successStepExecution = createStepExecution(1) // success
        // https://github.com/Flank/flank/issues/918
        // This test covers edge case where summary for both step and execution is null and outcome of
        // saved matrix was not changed and is set to success
        val malformed = createStepExecution(stepId = -666, executionId = -666) // flaky

        // below order in the list matters!
        val executions = listOf(
            successStepExecution,
            successStepExecution,
            malformed
        )

        val testMatrix = testMatrix().apply {
            testMatrixId = "123"
            state = FINISHED
            resultStorage = createResultsStorage()
            testExecutions = executions
        }

        val savedMatrix = SavedMatrix(testMatrix)

        assertEquals(expectedOutcome, savedMatrix.outcome)
    }
}
