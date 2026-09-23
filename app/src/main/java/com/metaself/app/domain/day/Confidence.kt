package com.metaself.app.domain.day

/**
 * How sure an estimate was — decision D7.
 *
 * A wrapped sandwich and an unidentifiable stew must not be reported with the same certainty, and
 * the doubt has to survive into the record rather than evaporating at the moment of saving.
 */
enum class Confidence { LOW, MEDIUM, HIGH }
