package com.metaself.app.domain.profile

/**
 * Biological sex, read only by the arithmetic that needs it.
 *
 * Mifflin-St Jeor carries a different constant per sex, and the conventional calorie floor differs
 * too. The trainer sends it with age and height (D84), because advice on effort depends on it;
 * nothing else reads it.
 */
enum class Sex { MALE, FEMALE }
