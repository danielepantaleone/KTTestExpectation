package io.github.danielepantaleone.kttestexpectation

import java.util.Timer
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.withLock

// region Global

/**
 * Reentrant lock for asynchronous await of expectation fulfillment and mutual exclusion.
 */
private val mutex: Lock = ReentrantLock()

/**
 * Condition variable to await for expectations fulfillment.
 */
private val condition: Condition = mutex.newCondition()

// endregion

// region Base expectation

/**
 * Exception raised when awaiting for expectations.
 *
 * @param message The exception message
 */
class KTTestException(message: String): RuntimeException(message)

/**
 * Contract for test expectations.
 */
interface KTTestExpectation {

    /**
     * A human readable string used to describe the expectation
     */
    val description: String

    /**
     * Whether this expectation has been fulfilled.
     */
    val isFulfilled: Boolean

}

// endregion

// region Test regular expectation

/**
 * A regular expectation to assess the outcome of an asynchronous test.
 * Expectation must be manually fulfilled using [fulfill].
 *
 * @property description A human readable string used to describe the expectation
 * @constructor Creates a new [KTRegularExpectation] with the provided description
 */
class KTRegularExpectation internal constructor(
    override val description: String
): KTTestExpectation {

    // region Private properties

    /**
     * Amount of times this expectation was fulfilled.
     */
    internal var fulfillmentCount: Int = 0

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

    // endregion

    // region Overridden properties

    override val isFulfilled: Boolean
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
 * Creates and returns a [KTRegularExpectation] to assess the outcome of an asynchronous test.
 * Expectation must be manually fulfilled using [KTRegularExpectation.fulfill].
 *
 * @param description The expectation description
 * @return [KTRegularExpectation]
 */
fun expectation(description: String): KTRegularExpectation {
    return KTRegularExpectation(description = description)
}

// endregion

// region Test predicate expectation

/**
 * A predicate that fulfills an expectation by returning true.
 */
typealias KTTestPredicate = () -> Boolean

/**
 * A special type of expectation that repeatedly evaluates its predicate until it becomes true.
 * Once the predicate has become true, it is expected to remain true and will not be evaluated again.
 *
 * Evaluation of the predicate is performed every 100ms on a separate thread.
 *
 * @property description A human readable string used to describe the expectation
 * @property predicate A predicate that fulfill this expectation when it becomes true
 * @constructor Creates a new [KTPredicateExpectation] with the provided description
 */
class KTPredicateExpectation internal constructor(
    override val description: String,
    private val predicate: KTTestPredicate
) : KTTestExpectation {

    // region Overridden properties

    override val isFulfilled: Boolean
        get() = mutex.withLock {
            return predicate()
        }

    // endregion

    override fun toString(): String {
        return description
    }

}

/**
 * Creates and returns a [KTPredicateExpectation].
 *
 * [KTPredicateExpectation] is a special type of expectation that repeatedly evaluates
 * its predicate until it becomes true. Once the predicate has become true, it is expected
 * to remain true and will not be evaluated again.
 *
 * Evaluation of the predicate is performed every 100ms on a separate thread.
 *
 * @param description The expectation description
 * @property predicate A predicate that fulfill this expectation when it becomes true
 * @return [KTPredicateExpectation]
 */
fun expectation(description: String, predicate: KTTestPredicate): KTPredicateExpectation {
    return KTPredicateExpectation(description = description, predicate = predicate)
}

// endregion

// region Expectation evaluation

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
 * Waits on a group of expectations for up to the specified timeout.
 *
 * @param expectations An array of expectations that must be fulfilled
 * @param time The maximum time to wait for expectations to be fulfilled
 * @param unit The time unit of the time argument
 * @throws KTTestException If any of the expectations was already fulfilled
 * @throws KTTestException If no expectation was provided
 * @throws KTTestException If any of the provided expectations cannot be fulfilled within the specified timeout
 */
@Throws(KTTestException::class)
fun waitForExpectations(vararg expectations: KTTestExpectation, time: Long, unit: TimeUnit) {
    waitForExpectations(expectations = expectations.toList(), time = time, unit = unit)
}

/**
 * Waits on a group of expectations for up to the specified timeout.
 *
 * @param expectations An array of expectations that must be fulfilled
 * @param time The maximum time to wait for expectations to be fulfilled
 * @param unit The time unit of the time argument
 * @throws KTTestException If any of the expectations was already fulfilled
 * @throws KTTestException If no expectation was provided
 * @throws KTTestException If any of the provided expectations cannot be fulfilled within the specified timeout
 */
@Suppress("KotlinConstantConditions")
@Throws(KTTestException::class)
fun waitForExpectations(expectations: List<KTTestExpectation>, time: Long, unit: TimeUnit) {
    if (expectations.isEmpty())
        throw KTTestException(message = "No expectation provided")
    fun allExpectationsFulfilled(): Boolean = expectations.all { it.isFulfilled }
    mutex.withLock {
        if (allExpectationsFulfilled())
            return
        // Attach a listener to regular expectations since they will be manually fulfilled
        val regularExpectations = expectations.filterIsInstance<KTRegularExpectation>()
        regularExpectations.forEach {
            it.fulfillmentListener = {
                mutex.withLock {
                    if (allExpectationsFulfilled()) {
                        condition.signalAll()
                    }
                }
            }
        }
        val predicateExpectations = expectations.filterIsInstance<KTPredicateExpectation>()
        var predicateTimer: Timer? = null
        if (predicateExpectations.isNotEmpty()) {
            fun allPredicateExpectationsFulfilled(): Boolean = predicateExpectations.all { it.isFulfilled }
            predicateTimer = fixedRateTimer(initialDelay = 100, period = 100) {
                mutex.withLock {
                    if (allPredicateExpectationsFulfilled()) {
                        predicateTimer?.cancel()
                    }
                    if (allExpectationsFulfilled()) {
                        condition.signalAll()
                    }
                }
            }
        }
        condition.await(time, unit)
        regularExpectations.forEach {
            it.fulfillmentListener = null
        }
        predicateTimer?.cancel()
        if (!allExpectationsFulfilled()) {
            throw KTTestException(message = "Exceeded expectation(s) time: ${expectations.filter { !it.isFulfilled }}")
        }
    }
}

// endregion