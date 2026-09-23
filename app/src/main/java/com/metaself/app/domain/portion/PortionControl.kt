package com.metaself.app.domain.portion

/**
 * Which control the owner should be given for a portion.
 *
 * A bowl of risotto and a slice of pizza want different things. "Half as much again" is a sensible
 * thing to say about 280 g and a silly thing to say about a slice — nobody eats 1.5 slices, they eat
 * one or two or three. The unit is what tells them apart.
 *
 * This lives beside the portion rules rather than beside the model's proposals, because a meal
 * repeated from the record needs the same answer to the same question.
 */
sealed interface PortionControl {

    /** Grams or millilitres: less / as described / more, or an exact amount. */
    data object Scale : PortionControl

    /** Slices, balls, eggs, biscuits: a whole number, with [current] the one in force. */
    data class Count(val current: Int) : PortionControl

    /** No number was ever recorded. No control at all is better than one that lies. */
    data object None : PortionControl
}
