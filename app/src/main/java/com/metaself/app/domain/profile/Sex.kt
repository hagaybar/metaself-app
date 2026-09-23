package com.metaself.app.domain.profile

/**
 * Biological sex, read only by the arithmetic that needs it.
 *
 * Mifflin-St Jeor carries a different constant per sex, and the conventional calorie floor differs
 * too. Nothing else in the app reads this field, and nothing else should.
 */
enum class Sex { MALE, FEMALE }
