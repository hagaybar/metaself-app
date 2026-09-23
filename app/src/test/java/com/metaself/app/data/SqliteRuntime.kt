package com.metaself.app.data

import org.junit.Assume.assumeFalse

/**
 * Stand aside on machines with no native SQLite runtime for Robolectric.
 *
 * Robolectric ships SQLite as a native library with no build for linux/aarch64, which is what this
 * development box is. CI runs on x86_64 and executes these tests for real, so the coverage is not
 * lost — it simply is not available here.
 *
 * **The condition is the ARCHITECTURE, never "did it throw".** Catching an exception instead would
 * swallow a genuine database defect on every machine that can actually run the test, which is all of
 * the ones that matter.
 *
 * Written once, here, rather than copied into each test that needs it.
 */
fun assumeSqliteRuntime() {
    assumeFalse(
        "Robolectric has no native SQLite runtime for linux/aarch64 — this test runs in CI",
        System.getProperty("os.arch") == "aarch64" &&
            System.getProperty("os.name").orEmpty().startsWith("Linux"),
    )
}
