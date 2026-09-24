package com.metaself.app.data.product

import com.google.common.truth.Truth.assertThat
import com.metaself.app.domain.amount.BelievableAmount.KCAL_PER_100G
import com.metaself.app.domain.amount.BelievableAmount.MACRO_PER_100G
import com.metaself.app.domain.product.Product
import org.junit.jupiter.api.Test

/**
 * A packet saved on this phone, read back as a product.
 *
 * A plain mapping, so it runs here without a database. It matters because the phone's own table is
 * read before the network on every scan: a packet saved with an impossible figure by a version older
 * than D39 (issue #31) would bring the crash back from there, network-free, for ever.
 */
class ProductEntityTest {

    /** The real figures from Bamba's entry in Open Food Facts, checked on 2026-09-04. */
    private val bamba = Product(
        barcode = "7290000066318",
        name = "במבה",
        brand = "דוגמה",
        kcalPer100g = 535.0,
        proteinPer100g = 17.0,
        carbsPer100g = 49.0,
        fatPer100g = 30.0,
        servingSizeG = 80.0,
    )

    private val saved = bamba.toEntity(savedAtMillis = 1_758_000_000_000)

    @Test
    fun `a saved packet reads back as the product it was`() {
        assertThat(saved.toDomain()).isEqualTo(bamba)
    }

    /**
     * Not believed, so the scan asks Open Food Facts as if it had never seen the barcode (D39).
     * A not-a-number most likely cannot be stored in these columns today — inferred by reading,
     * not verified: SQLite binds NaN as NULL and the columns are REAL NOT NULL. Pinned anyway,
     * since the rule is the value, whatever put it there.
     */
    @Test
    fun `a saved packet with an infinite or negative figure is not believed`() {
        assertThat(saved.copy(kcalPer100g = Double.POSITIVE_INFINITY).toDomain()).isNull()
        assertThat(saved.copy(fatPer100g = -5.0).toDomain()).isNull()
        assertThat(saved.copy(fatPer100g = Double.NaN).toDomain()).isNull()
    }

    /**
     * A packet saved before D42 (issue #32) may hold 1e12 kcal per 100 g — the label form took any
     * finite figure then. The phone's own table is read first on every scan, so it is judged by the
     * same ceiling as a reply from the network: not believed, and asked afresh. At the ceiling it is
     * the product.
     */
    @Test
    fun `a stored packet with a figure past its ceiling is not believed`() {
        assertThat(saved.copy(kcalPer100g = 1e12).toDomain()).isNull()
        assertThat(saved.copy(proteinPer100g = MACRO_PER_100G + 0.5).toDomain()).isNull()

        assertThat(saved.copy(kcalPer100g = KCAL_PER_100G).toDomain())
            .isEqualTo(bamba.copy(kcalPer100g = KCAL_PER_100G))
    }
}
