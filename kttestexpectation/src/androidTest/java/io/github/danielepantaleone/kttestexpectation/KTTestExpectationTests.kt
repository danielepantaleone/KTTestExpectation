package io.github.danielepantaleone.kttestexpectation

import android.os.Handler
import android.os.Looper
import junit.framework.TestCase
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class KTTestExpectationTests {

    private var callbacks = mutableListOf<Runnable>()
    private var handler: Handler? = null

    // region Initialization

    @Before
    fun setUp() {
        callbacks.clear()
        handler = Handler(Looper.getMainLooper())
    }

    @After
    fun tearDown() {
        callbacks.forEach { handler?.removeCallbacks(it) }
        callbacks.clear()
        handler = null
    }

    // endregion

    // region Tests

    @Test
    fun testAwaitSingleExpectationWithFulfillment() {
        val expectation = expectation("Expectation")
        runLater(200, expectation::fulfill)
        waitForExpectation(
            expectation = expectation,
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        TestCase.assertTrue(expectation.isFulfilled)
    }

    @Test
    fun testAwaitSingleExpectationWithFulfillmentAfterTimeout() {
        val expectation = expectation("Expectation with fulfillment after timeout")
        runLater(500, expectation::fulfill)
        try {
            waitForExpectation(
                expectation = expectation,
                time = 200,
                unit = TimeUnit.MILLISECONDS
            )
            TestCase.fail("Expected KTTestException due to exceeding expectation timeout")
        } catch (_: KTTestException) {
            TestCase.assertFalse(expectation.isFulfilled)
        }
    }

    @Test
    fun testAwaitSingleExpectationWithoutFulfillment() {
        val expectation = expectation("Expectation without fulfillment")
        try {
            waitForExpectation(
                expectation = expectation,
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            TestCase.fail("Expected KTTestException due to exceeding expectation timeout")
        } catch (_: KTTestException) {
            TestCase.assertFalse(expectation.isFulfilled)
        }
    }

    @Test
    fun testNegativeExpectedFulfillmentCount() {
        val expectation = expectation("Expectation")
        expectation.expectedFulfillmentCount = -1
        try {
            expectation.fulfill()
            TestCase.fail("Expected KTTestException due to negative expectedFulfillmentCount")
        } catch (e: KTTestException) {
            TestCase.assertEquals("Expectation expected fulfillment count must be greater than 0", e.message)
        }
    }

    @Test
    fun testAwaitMultiExpectationsWithFulfillment() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        runLater(200, expectation1::fulfill)
        runLater(300, expectation2::fulfill)
        waitForExpectations(
            expectations = listOf(expectation1, expectation2),
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        TestCase.assertTrue(expectation1.isFulfilled)
        TestCase.assertTrue(expectation2.isFulfilled)
    }

    @Test
    fun testAwaitMultiExpectationsWithFulfillmentAndDifferentCount() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        expectation2.expectedFulfillmentCount = 2
        runLater(200, expectation1::fulfill)
        runLater(100, expectation2::fulfill)
        runLater(300, expectation2::fulfill)
        waitForExpectations(
            expectations = listOf(expectation1, expectation2),
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        TestCase.assertTrue(expectation1.isFulfilled)
        TestCase.assertTrue(expectation2.isFulfilled)
    }

    @Test
    fun testAwaitMultiExpectationsWithSingleFulfillmentAfterTimeout() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        runLater(200, expectation1::fulfill)
        runLater(700, expectation2::fulfill)
        try {
            waitForExpectations(
                expectations = listOf(expectation1, expectation2),
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            TestCase.fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 2'")
        } catch (e: KTTestException) {
            TestCase.assertTrue(expectation1.isFulfilled)
            TestCase.assertFalse(expectation2.isFulfilled)
            val message = checkNotNull(e.message)
            TestCase.assertTrue("Expect exception message to contain 'Expectation 2'", message.contains("Expectation 2"))
            TestCase.assertFalse("Expect exception message not to contain 'Expectation 1'", message.contains("Expectation 1"))
        }
    }

    @Test
    fun testAwaitMultiExpectationsWithSingleFulfillmentAfterTimeoutAndDifferentCount() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        expectation2.expectedFulfillmentCount = 2
        runLater(200, expectation1::fulfill)
        runLater(100, expectation2::fulfill)
        try {
            waitForExpectations(
                expectations = listOf(expectation1, expectation2),
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            TestCase.fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 2'")
        } catch (e: KTTestException) {
            TestCase.assertTrue(expectation1.isFulfilled)
            TestCase.assertFalse(expectation2.isFulfilled)
            val message = checkNotNull(e.message)
            TestCase.assertTrue("Expect exception message to contain 'Expectation 2'", message.contains("Expectation 2"))
            TestCase.assertFalse("Expect exception message not to contain 'Expectation 1'", message.contains("Expectation 1"))
        }
    }

    @Test
    fun testAwaitMultiExpectationsWithoutFulfillment() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        try {
            waitForExpectations(
                expectations = listOf(expectation1, expectation2),
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            TestCase.fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 1' and 'Expectation 2'")
        } catch (e: KTTestException) {
            TestCase.assertFalse(expectation1.isFulfilled)
            TestCase.assertFalse(expectation2.isFulfilled)
            val message = checkNotNull(e.message)
            TestCase.assertTrue("Expect exception message to contain 'Expectation 1'", message.contains("Expectation 1"))
            TestCase.assertTrue("Expect exception message to contain 'Expectation 2'", message.contains("Expectation 2"))
        }
    }

    @Test
    fun testAwaitMultiExpectationsWithAlreadyFulfilledExpectation() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        expectation2.fulfill()
        runLater(200, expectation1::fulfill)
        runLater(300, expectation2::fulfill)
        waitForExpectations(
            expectations = listOf(expectation1, expectation2),
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        TestCase.assertTrue(expectation1.isFulfilled)
        TestCase.assertTrue(expectation2.isFulfilled)
    }

    @Test
    fun testAwaitMultiWithEmptyList() {
        try {
            waitForExpectations(
                expectations = emptyList(),
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            TestCase.fail("Expected KTTestException due to no expectation provided")
        } catch (e: KTTestException) {
            TestCase.assertEquals("No expectation provided", e.message)
        }
    }

    // endregion

    // region Internals

    private fun runLater(delayMillis: Long, callback: Runnable) {
        callbacks.add(callback)
        handler?.postDelayed(callback, delayMillis)
    }

    // endregion

}