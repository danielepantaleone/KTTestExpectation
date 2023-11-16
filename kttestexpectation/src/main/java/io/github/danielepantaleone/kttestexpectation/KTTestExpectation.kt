package io.github.danielepantaleone.kttestexpectation

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.jvm.Throws

/**
 * Reentrant lock for asynchronous await of expectation fulfillment and mutual exclusion.
 */
private val mutex: Lock = ReentrantLock()

/**
 * Condition variable to await for expectations fulfillment.
 */
private val condition: Condition = mutex.newCondition()

/**
 * Exception raised when awaiting for expectations.
 *
 * @param message The exception message
 */
class KTTestException(message: String): RuntimeException(message)

/**
 * An expected outcome in an asynchronous test.
 *
 * @property description A human readable string used to describe the expectation.
 * @constructor Creates a new [KTTestExpectation] with the provided description.
 */
class KTTestExpectation internal constructor(private val description: String) {

    // region Private properties

    /**
     * Amount of times this expectation was fulfilled.
     */
    private var fulfillmentCount: Int = 0

    // endregion

    // region Properties

    /**
     * A callback to be invoked when an expectation is fulfilled.
     */
    internal var fulfillmentListener: ((KTTestExpectation) -> Unit)? = null

    /**
     * If set, calls to [fulfill] after the expectation has already been fulfilled, exceeding
     * the fulfillment count - will raise a [KTTestException].
     */
    var assertForOverFulfill: Boolean = true

    /**
     * The [expectedFulfillmentCount] is the number of times [fulfill] must be called on the expectation in order for it
     * to report complete fulfillment to its waiter. By default, expectations have an [expectedFulfillmentCount] of 1.
     * This value must be greater than 0.
     */
    var expectedFulfillmentCount: Int = 1

    /**
     * Whether this expectation has been fulfilled.
     */
    val isFulfilled: Boolean
        get() = mutex.withLock {
            return expectedFulfillmentCount in 1..fulfillmentCount
        }

    // endregion

    // region Interface

    /**
     * Call fulfill to mark an expectation as having been met. It's an error to call fulfill on an
     * expectation more times than its [expectedFulfillmentCount] value specifies, or when the test case
     * that vended the expectation has already completed.
     *
     * @throws KTTestException If the expectation expected fulfillment count is <= 0 and [assertForOverFulfill] is set to true
     * @throws KTTestException If the expectation was already fulfilled
     */
    @Throws(KTTestException::class)
    fun fulfill() {
        val isFulfilled: Boolean
        mutex.lock()
        try {
            if (expectedFulfillmentCount <= 0)
                throw KTTestException("Expectation expected fulfillment count must be greater than 0")
            if (assertForOverFulfill && fulfillmentCount >= expectedFulfillmentCount)
                throw KTTestException("Expectation already fulfilled: $this")
            fulfillmentCount += 1
            isFulfilled = fulfillmentCount >= expectedFulfillmentCount
        } finally {
            mutex.unlock()
        }
        if (isFulfilled) {
            fulfillmentListener?.invoke(this)
        }
    }

    // endregion

    override fun toString(): String {
        return description
    }

}

/**
 * Creates and returns a [KTTestExpectation].
 *
 * @param description The expectation description
 * @return [KTTestExpectation]
 */
fun expectation(description: String): KTTestExpectation {
    return KTTestExpectation(description = description)
}

/**
 * Waits on an expectation for up to the specified timeout.
 *
 * @param expectation The expectation that must be fulfilled
 * @param time The maximum time to wait for expectations to be fulfilled
 * @param unit The time unit of the time argument
 * @throws KTTestException If the expectation was already fulfilled
 * @throws KTTestException If the expectation cannot be fulfilled within the specified timeout
 */
@Throws(KTTestException::class)
fun waitForExpectation(expectation: KTTestExpectation, time: Long, unit: TimeUnit) {
    waitForExpectations(expectations = listOf(expectation), time = time, unit = unit)
}

/**
 * Waits on a group of expectations for up to the specified timeout
 *
 * @param expectations An array of expectations that must be fulfilled
 * @param time The maximum time to wait for expectations to be fulfilled
 * @param unit The time unit of the time argument
 * @throws KTTestException If any of the expectations was already fulfilled
 * @throws KTTestException If no expectation was provided
 * @throws KTTestException If any of the provided expectations cannot be fulfilled within the specified timeout
 */
@Throws(KTTestException::class)
fun waitForExpectations(expectations: List<KTTestExpectation>, time: Long, unit: TimeUnit) {
    if (expectations.isEmpty())
        throw KTTestException(message = "No expectation provided")
    fun allExpectationsFulfilled(): Boolean = expectations.all { it.isFulfilled }
    mutex.withLock {
        if (allExpectationsFulfilled())
            return
        expectations.forEach {
            it.fulfillmentListener = {
                mutex.withLock {
                    if (allExpectationsFulfilled()) {
                        condition.signalAll()
                    }
                }
            }
        }
        condition.await(time, unit)
        expectations.forEach {
            it.fulfillmentListener = null
        }
        if (!allExpectationsFulfilled()) {
            throw KTTestException(message = "Exceeded expectation(s) time: ${expectations.filter { !it.isFulfilled }}")
        }
    }
}