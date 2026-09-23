package com.metaself.app.data.product

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.metaself.app.domain.product.Product

/**
 * A product this phone has seen before.
 *
 * The point of this table is not speed, it is independence. Decision D8 says logging never depends
 * on the network; a barcode that only works online would break that for every packet in the
 * cupboard. Scanned once, it works for ever afterwards in aeroplane mode.
 *
 * Keyed by the barcode itself rather than by a generated id: the barcode IS the identity, and a
 * second scan of the same packet should replace what is stored rather than accumulate rows.
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val barcode: String,
    val name: String,
    val brand: String?,
    val kcalPer100g: Double,
    val proteinPer100g: Double,
    val carbsPer100g: Double,
    val fatPer100g: Double,
    val servingSizeG: Double?,
    val savedAtMillis: Long,
)

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE barcode = :barcode")
    suspend fun find(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(product: ProductEntity)

}

/**
 * The stored packet as a product, or null when the row cannot be one. That includes a figure that
 * is no quantity of food: a row saved before D39 (issue #31) may hold one, and believing it would
 * bring the crash back from the phone's own table. The same for a figure past its per-100 g ceiling
 * (D42, issue #32) — the label form took 1e12 kcal before then — judged exactly as a reply from the
 * network is. The lookup then asks the network as for a new barcode.
 */
fun ProductEntity.toDomain(): Product? = runCatching {
    Product(
        barcode = barcode,
        name = name,
        brand = brand,
        kcalPer100g = kcalPer100g,
        proteinPer100g = proteinPer100g,
        carbsPer100g = carbsPer100g,
        fatPer100g = fatPer100g,
        servingSizeG = servingSizeG,
    )
}.getOrNull()?.takeIf { it.figuresAreFood }

fun Product.toEntity(savedAtMillis: Long): ProductEntity = ProductEntity(
    barcode = barcode,
    name = name,
    brand = brand,
    kcalPer100g = kcalPer100g,
    proteinPer100g = proteinPer100g,
    carbsPer100g = carbsPer100g,
    fatPer100g = fatPer100g,
    servingSizeG = servingSizeG,
    savedAtMillis = savedAtMillis,
)
