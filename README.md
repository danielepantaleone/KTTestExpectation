# KTTestExpectation

[![API](https://img.shields.io/badge/API-23%2B-green.svg?style=flat-square)](https://android-arsenal.com/api?level=23)
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/danielepantaleone/KTTestExpectation?style=flat-square)
![GitHub](https://img.shields.io/github/license/danielepantaleone/KTTestExpectation?style=flat-square)
[![GitHub Workflow Status (with event)](https://img.shields.io/github/actions/workflow/status/danielepantaleone/KTTestExpectation/android-tests.yml?style=flat-square&logo=github)](https://github.com/danielepantaleone/KTTestExpectation/actions/workflows/android-tests.yml)

The purpose of this library is to provide simple Kotlin implementation of Swift's expectation to ease testing of asynchronous code that doesn't make use of Kotlin coroutines.

## Table of contents

* [Basic usage](#basic-usage)
* [Advanced usage](#advanced-usage)
* [Contributing](#contributing)
* [License](#license)

## Basic usage

Awaiting on a single expectation:

```kotlin
@Test
fun testAwaitSingleExpectation() {
    val expectation = expectation("Expectation")
    asyncLongOperationWithCallback {
        expectation.fulfill()
    }
    waitForExpectation(
        expectation = expectation,
        time = 10,
        unit = TimeUnit.SECONDS
    )
}
```

Awaiting on multiple expectations:

```kotlin
@Test
fun testAwaitMultipleExpectations() {
    val expectation1 = expectation("Expectation 1")
    val expectation2 = expectation("Expectation 2")
    asyncLongOperationWithCallback {
        expectation1.fulfill()
    }
    asyncLongerOperationWithCallback {
        expectation2.fulfill()
    }
    waitForExpectations(
        expectations = listOf(expectation1, expectation2),
        time = 20,
        unit = TimeUnit.SECONDS
    )
}
```

## Advanced usage

Awaiting on multiple expectations with different expected fulfillment count:

```kotlin
@Test
fun testAwaitMultipleExpectations() {
    val expectation1 = expectation("Expectation 1")
    val expectation2 = expectation("Expectation 2")
    expectation2.expectedFulfillmentCount = 2
    asyncLongOperationWithCallback {
        expectation1.fulfill()
    }
    asyncLongerOperationWithCallback {
        expectation2.fulfill()
        asyncLongestLongerOperationWithCallback {
            expectation2.fulfill()
        }   
    }
    waitForExpectations(
        expectations = listOf(expectation1, expectation2),
        time = 60,
        unit = TimeUnit.SECONDS
    )
}
```

## Contributing

If you like this project you can contribute it by:

- Submit a bug report by opening an [issue](https://github.com/danielepantaleone/KTTestExpectation/issues)
- Submit code by opening a [pull request](https://github.com/danielepantaleone/KTTestExpectation/pulls)

## License

```
MIT License

Copyright (c) 2023 Daniele Pantaleone

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
