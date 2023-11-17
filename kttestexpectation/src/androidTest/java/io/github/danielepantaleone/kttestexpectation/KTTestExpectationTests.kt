package io.github.danielepantaleone.kttestexpectation

import android.os.Handler
import android.os.Looper
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import junit.framework.TestCase.fail
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        runAfterDelay(200, expectation::fulfill)
        waitForExpectation(
            expectation = expectation,
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(expectation.isFulfilled)
    }

    @Test
    fun testAwaitSingleExpectationWithOverFulfillmentAndAssertForOverFulfillToFalse() {
        val expectation = expectation("Expectation")
        expectation.assertForOverFulfill = false
        expectation.fulfill()
        expectation.fulfill()
        assertTrue(expectation.isFulfilled)
        assertEquals(2, expectation.fulfillmentCount)
    }

    @Test
    fun testAwaitSingleExpectationWithFulfillmentAfterTimeout() {
        val expectation = expectation("Expectation with fulfillment after timeout")
        runAfterDelay(500, expectation::fulfill)
        try {
            waitForExpectation(
                expectation = expectation,
                time = 200,
                unit = TimeUnit.MILLISECONDS
            )
            fail("Expected KTTestException due to exceeding expectation timeout")
        } catch (_: KTTestException) {
            assertFalse(expectation.isFulfilled)
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
            fail("Expected KTTestException due to exceeding expectation timeout")
        } catch (_: KTTestException) {
            assertFalse(expectation.isFulfilled)
        }
    }

    @Test
    fun testNegativeExpectedFulfillmentCount() {
        val expectation = expectation("Expectation")
        expectation.expectedFulfillmentCount = -1
        try {
            expectation.fulfill()
            fail("Expected KTTestException due to negative expectedFulfillmentCount")
        } catch (e: KTTestException) {
            assertEquals("Expectation expected fulfillment count must be greater than 0", e.message)
        }
    }

    @Test
    fun testAwaitMultiExpectationsWithFulfillment() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        runAfterDelay(200, expectation1::fulfill)
        runAfterDelay(300, expectation2::fulfill)
        waitForExpectations(
            expectations = listOf(expectation1, expectation2),
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(expectation1.isFulfilled)
        assertTrue(expectation2.isFulfilled)
    }

    @Test
    fun testAwaitMultiExpectationsWithFulfillmentAndDifferentCount() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        expectation2.expectedFulfillmentCount = 2
        runAfterDelay(200, expectation1::fulfill)
        runAfterDelay(100, expectation2::fulfill)
        runAfterDelay(300, expectation2::fulfill)
        waitForExpectations(
            expectations = listOf(expectation1, expectation2),
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(expectation1.isFulfilled)
        assertTrue(expectation2.isFulfilled)
    }

    @Test
    fun testAwaitMultiExpectationsWithSingleFulfillmentAfterTimeout() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        runAfterDelay(200, expectation1::fulfill)
        runAfterDelay(700, expectation2::fulfill)
        try {
            waitForExpectations(
                expectations = listOf(expectation1, expectation2),
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 2'")
        } catch (e: KTTestException) {
            assertTrue(expectation1.isFulfilled)
            assertFalse(expectation2.isFulfilled)
            val message = checkNotNull(e.message)
            assertTrue("Expect exception message to contain 'Expectation 2'", message.contains("Expectation 2"))
            assertFalse("Expect exception message not to contain 'Expectation 1'", message.contains("Expectation 1"))
        }
    }

    @Test
    fun testAwaitMultiExpectationsWithSingleFulfillmentAfterTimeoutAndDifferentCount() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        expectation2.expectedFulfillmentCount = 2
        runAfterDelay(200, expectation1::fulfill)
        runAfterDelay(100, expectation2::fulfill)
        try {
            waitForExpectations(
                expectations = listOf(expectation1, expectation2),
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 2'")
        } catch (e: KTTestException) {
            assertTrue(expectation1.isFulfilled)
            assertFalse(expectation2.isFulfilled)
            val message = checkNotNull(e.message)
            assertTrue("Expect exception message to contain 'Expectation 2'", message.contains("Expectation 2"))
            assertFalse("Expect exception message not to contain 'Expectation 1'", message.contains("Expectation 1"))
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
            fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 1' and 'Expectation 2'")
        } catch (e: KTTestException) {
            assertFalse(expectation1.isFulfilled)
            assertFalse(expectation2.isFulfilled)
            val message = checkNotNull(e.message)
            assertTrue("Expect exception message to contain 'Expectation 1'", message.contains("Expectation 1"))
            assertTrue("Expect exception message to contain 'Expectation 2'", message.contains("Expectation 2"))
        }
    }

    @Test
    fun testAwaitMultiExpectationsWithAlreadyFulfilledExpectation() {
        val expectation1 = expectation("Expectation 1")
        val expectation2 = expectation("Expectation 2")
        expectation2.fulfill()
        runAfterDelay(200, expectation1::fulfill)
        runAfterDelay(300, expectation2::fulfill)
        waitForExpectations(
            expectations = listOf(expectation1, expectation2),
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(expectation1.isFulfilled)
        assertTrue(expectation2.isFulfilled)
    }

    @Test
    fun testAwaitMultiWithEmptyList() {
        try {
            waitForExpectations(
                expectations = emptyList(),
                time = 500,
                unit = TimeUnit.MILLISECONDS
            )
            fail("Expected KTTestException due to no expectation provided")
        } catch (e: KTTestException) {
            assertEquals("No expectation provided", e.message)
        }
    }

    @Test
    fun testAwaitSinglePredicateExpectation() {
        val mutex = ReentrantLock()
        var willBeSetToTrue = false
        val expectation = expectation("Expectation") { mutex.withLock { willBeSetToTrue } }
        runAfterDelay(200) {
            mutex.withLock {
                willBeSetToTrue = true
            }
        }
        waitForExpectation(
            expectation = expectation,
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(expectation.isFulfilled)
    }

    @Test
    fun testAwaitMultiplePredicateExpectations() {
        val mutex = ReentrantLock()
        var willBeSetToTrueOne = false
        var willBeSetToTrueTwo = false
        val expectation1 = expectation("Expectation 1") { mutex.withLock { willBeSetToTrueOne } }
        val expectation2 = expectation("Expectation 2") { mutex.withLock { willBeSetToTrueTwo } }
        runAfterDelay(200) { mutex.withLock { willBeSetToTrueOne = true } }
        runAfterDelay(300) { mutex.withLock { willBeSetToTrueTwo = true } }
        waitForExpectations(
            expectations = listOf(expectation1, expectation2),
            time = 500,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(expectation1.isFulfilled)
        assertTrue(expectation2.isFulfilled)
    }

    @Test
    fun testAwaitMultipleMixedTypeExpectations() {
        val mutex = ReentrantLock()
        var willBeSetToTrueOne = false
        var willBeSetToTrueTwo = false
        val expectation1 = expectation("Expectation 1") { mutex.withLock { willBeSetToTrueOne } }
        val expectation2 = expectation("Expectation 2") { mutex.withLock { willBeSetToTrueTwo } }
        val expectation3 = expectation("Expectation 3")
        expectation3.expectedFulfillmentCount = 2
        runAfterDelay(200) { mutex.withLock { willBeSetToTrueOne = true } }
        runAfterDelay(300) { expectation3.fulfill() }
        runAfterDelay(500) { mutex.withLock { willBeSetToTrueTwo = true } }
        runAfterDelay(700) { expectation3.fulfill() }
        waitForExpectations(
            expectations = listOf(expectation1, expectation2, expectation3),
            time = 1000,
            unit = TimeUnit.MILLISECONDS
        )
        assertTrue(expectation1.isFulfilled)
        assertTrue(expectation2.isFulfilled)
        assertTrue(expectation3.isFulfilled)
    }

    @Test
    fun testAwaitMultipleMixedTypeExpectationsWithFailureOnPredicate() {
        val mutex = ReentrantLock()
        var willBeSetToTrueOne = false
        val willBeSetToTrueTwo = false
        val expectation1 = expectation("Expectation 1") { mutex.withLock { willBeSetToTrueOne } }
        val expectation2 = expectation("Expectation 2") { mutex.withLock { willBeSetToTrueTwo } }
        val expectation3 = expectation("Expectation 3")
        expectation3.expectedFulfillmentCount = 2
        runAfterDelay(200) { mutex.withLock { willBeSetToTrueOne = true } }
        runAfterDelay(300) { expectation3.fulfill() }
        runAfterDelay(700) { expectation3.fulfill() }
        try {
            waitForExpectations(
                expectations = listOf(expectation1, expectation2, expectation3),
                time = 1000,
                unit = TimeUnit.MILLISECONDS
            )
            fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 2'")
        } catch (_: KTTestException) {
            assertTrue(expectation1.isFulfilled)
            assertFalse(expectation2.isFulfilled)
            assertTrue(expectation3.isFulfilled)
        }
    }

    @Test
    fun testAwaitMultipleMixedTypeExpectationsWithFailureOnRegular() {
        val mutex = ReentrantLock()
        var willBeSetToTrueOne = false
        var willBeSetToTrueTwo = false
        val expectation1 = expectation("Expectation 1") { mutex.withLock { willBeSetToTrueOne } }
        val expectation2 = expectation("Expectation 2") { mutex.withLock { willBeSetToTrueTwo } }
        val expectation3 = expectation("Expectation 3")
        expectation3.expectedFulfillmentCount = 2
        runAfterDelay(200) { mutex.withLock { willBeSetToTrueOne = true } }
        runAfterDelay(300) { expectation3.fulfill() }
        runAfterDelay(500) { mutex.withLock { willBeSetToTrueTwo = true } }
        try {
            waitForExpectations(
                expectations = listOf(expectation1, expectation2, expectation3),
                time = 1000,
                unit = TimeUnit.MILLISECONDS
            )
            fail("Expected KTTestException due to exceeding expectation timeout of 'Expectation 2'")
        } catch (_: KTTestException) {
            assertTrue(expectation1.isFulfilled)
            assertTrue(expectation2.isFulfilled)
            assertFalse(expectation3.isFulfilled)
        }
    }

    // endregion

    // region Internals

    private fun runAfterDelay(delayMillis: Long, callback: Runnable) {
        callbacks.add(callback)
        handler?.postDelayed(callback, delayMillis)
    }

    // endregion

}