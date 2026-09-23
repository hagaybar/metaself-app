# R8 runs on the release build (isMinifyEnabled = true) from step 1 deliberately, so that anything
# it strips is discovered on the first install rather than months later. The failure this guards
# against is a known one: R8 removing a backup serializer, silently, in a shipped build, so that
# the loss only surfaces when somebody tries to restore.
#
# No keeps are needed yet — nothing here is reached by reflection. Add them beside the feature that
# needs them, with a comment naming what breaks without the keep.

# ---------------------------------------------------------------------------------------------
# Step 7: the encrypted API key store.
#
# androidx.security:security-crypto is built on Google Tink, which is annotated with Error Prone's
# @Immutable. Those annotations are compile-time only and are not on the runtime classpath, so R8
# reports the class as missing and refuses to finish. Nothing is stripped and nothing breaks —
# there is genuinely nothing to warn about — so the warning is suppressed rather than the class
# kept.
#
# Found by the release build, which is the only place it appears: the debug build does not run R8.
-dontwarn com.google.errorprone.annotations.**
