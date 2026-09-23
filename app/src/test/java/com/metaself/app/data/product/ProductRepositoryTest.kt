package com.metaself.app.data.product

import com.google.common.truth.Truth.assertThat
import com.metaself.app.data.diagnostics.ProblemLog
import com.metaself.app.domain.product.ProductField
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * What a scan keeps on the phone, with the network replaced by a reply written here.
 *
 * No Room: the products table is a map standing in for [ProductDao], so nothing here needs the
 * SQLite runtime this machine lacks and nothing skips. What is under test is the order of the
 * lookup — phone first, then the database, and what gets written back — not the SQL.
 */
class ProductRepositoryTest {

    private val barcode = "7290000066318"

    /** The real reply, fetched from the live database on 2026-09-04 and committed unchanged. */
    private val bambaReply: String =
        javaClass.classLoader!!.getResourceAsStream("openfoodfacts-bamba.json")!!
            .readBytes()
            .decodeToString()

    /** Bamba as the database might hold it with the fat left out — the case issue #30 names. */
    private val withoutFat = """{"code":"$barcode","product":{"product_name":"במבה",""" +
        """"brands":"אסם","serving_size":"1 serving (80 g)","nutriments":""" +
        """{"energy-kcal_100g":535,"proteins_100g":17,"carbohydrates_100g":49}}}"""

    private fun complete(fat: String) = """{"code":"$barcode","product":{"product_name":"במבה",""" +
        """"brands":"אסם","nutriments":{"energy-kcal_100g":535,"proteins_100g":17,""" +
        """"carbohydrates_100g":49,"fat_100g":$fat}}}"""

    private class InMemoryProducts : ProductDao {
        val rows = mutableMapOf<String, ProductEntity>()
        var saves = 0

        override suspend fun find(barcode: String): ProductEntity? = rows[barcode]

        override suspend fun save(product: ProductEntity) {
            saves++
            rows[product.barcode] = product
        }
    }

    private val dao = InMemoryProducts()
    private var fetches = 0

    private fun repository(reply: String?) = ProductRepository(
        products = dao,
        problems = ProblemLog.NONE,
        fetchReply = { fetches++; reply },
    )

    /**
     * The trap the issue sets: the lookup used to save every product it built before returning it,
     * so an invented 0.0 was on the phone from the first scan and every later scan found it there.
     * An incomplete reply is now returned before anything is written, so the next scan misses on
     * the phone and asks the database again — and the form comes back (D40).
     */
    @Test
    fun `an incomplete reply is not saved, and the next scan asks the database again`() = runTest {
        val products = repository(withoutFat)

        val first = products.lookUp(barcode, nowMillis = 1L)
        val second = products.lookUp(barcode, nowMillis = 2L)

        listOf(first, second).forEach { result ->
            assertThat(result).isInstanceOf(Lookup.Incomplete::class.java)
            assertThat((result as Lookup.Incomplete).missing).containsExactly(ProductField.FAT)
            assertThat(result.form.fatPer100g).isEqualTo("")
        }
        assertThat(dao.saves).isEqualTo(0)
        assertThat(fetches).isEqualTo(2)
        assertThat(dao.find(barcode)).isNull()
    }

    /** D8, unchanged: a packet scanned once works with no signal from then on. */
    @Test
    fun `a complete reply is saved and the next scan needs no network`() = runTest {
        val products = repository(bambaReply)

        val first = products.lookUp(barcode, nowMillis = 1L) as Lookup.Found
        assertThat(first.fromThisPhone).isFalse()
        assertThat(dao.saves).isEqualTo(1)

        val second = products.lookUp(barcode, nowMillis = 2L) as Lookup.Found
        assertThat(second.fromThisPhone).isTrue()
        assertThat(second.product).isEqualTo(first.product)
        assertThat(fetches).isEqualTo(1)
    }

    /** A 0 the database states is a figure like any other, and is kept as one. */
    @Test
    fun `a stated zero is saved like any figure`() = runTest {
        val result = repository(complete(fat = "0")).lookUp(barcode, nowMillis = 1L)

        assertThat(result).isInstanceOf(Lookup.Found::class.java)
        assertThat((result as Lookup.Found).product.fatPer100g).isEqualTo(0.0)
        assertThat(dao.saves).isEqualTo(1)
        assertThat(dao.find(barcode)!!.fatPer100g).isEqualTo(0.0)
    }

    /** "Never heard of it" and "could not ask" stay what they were. */
    @Test
    fun `an unusable reply is unknown, a missing reply is unreachable`() = runTest {
        assertThat(repository(complete(fat = "-5")).lookUp(barcode, nowMillis = 1L))
            .isEqualTo(Lookup.Unknown)
        assertThat(dao.saves).isEqualTo(0)

        assertThat(repository(null).lookUp(barcode, nowMillis = 1L)).isEqualTo(Lookup.Unreachable)
        assertThat(dao.saves).isEqualTo(0)
    }

    /**
     * Once he copies the fat off the packet and saves, the packet is his typed product: kept on the
     * phone and found there, with his figure, and the database is not asked again.
     */
    @Test
    fun `what he types for the missing figure is kept and found from the phone`() = runTest {
        val products = repository(withoutFat)
        val incomplete = products.lookUp(barcode, nowMillis = 1L) as Lookup.Incomplete

        products.remember(incomplete.form.copy(fatPer100g = "30").toProduct()!!, nowMillis = 2L)
        val found = products.lookUp(barcode, nowMillis = 3L)

        assertThat(found).isInstanceOf(Lookup.Found::class.java)
        assertThat((found as Lookup.Found).fromThisPhone).isTrue()
        assertThat(found.product.fatPer100g).isEqualTo(30.0)
        assertThat(found.product.kcalPer100g).isEqualTo(535.0)
        assertThat(fetches).isEqualTo(1)
    }
}
