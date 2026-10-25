package com.example.kasirarn.ui.ingredient

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.kasirarn.R
import com.example.kasirarn.data.db.IngredientEntity
import com.example.kasirarn.data.db.StockMoveType
import com.example.kasirarn.data.repo.Reason
import com.example.kasirarn.data.repo.dimension
import com.example.kasirarn.domain.Dimension
import com.example.kasirarn.domain.UnitDef
import com.example.kasirarn.ui.components.StatusPill
import com.example.kasirarn.ui.components.Tone

enum class StockLevel { OK, LOW, OUT }

fun IngredientEntity.level(): StockLevel = when {
    stokDasar <= 0 -> StockLevel.OUT
    stokMinimum > 0 && stokDasar <= stokMinimum -> StockLevel.LOW
    else -> StockLevel.OK
}

@Composable
fun StockLevelPill(level: StockLevel) {
    when (level) {
        StockLevel.OK -> StatusPill(stringResource(R.string.stock_ok), Tone.GOOD)
        StockLevel.LOW -> StatusPill(stringResource(R.string.stock_low), Tone.WARN)
        StockLevel.OUT -> StatusPill(stringResource(R.string.stock_out), Tone.BAD)
    }
}

/** Satuan standar yang paling pas untuk menampilkan nilai awal di isian (5000 g menjadi 5 kg). */
fun bestUnit(base: Long, dimension: Dimension, units: List<UnitDef>): UnitDef {
    if (dimension != Dimension.COUNT && base > 0 && base % 1000 == 0L) return dimension.standardUnits[1]
    return units.first()
}

@Composable
fun dimensionLabel(d: Dimension): String = stringResource(
    when (d) {
        Dimension.MASS -> R.string.dimension_mass
        Dimension.VOLUME -> R.string.dimension_volume
        Dimension.COUNT -> R.string.dimension_count
    },
)

@Composable
fun reasonLabel(code: String): String = when (code) {
    Reason.INITIAL -> stringResource(R.string.reason_initial)
    Reason.DAMAGED -> stringResource(R.string.reason_damaged)
    Reason.SPILLED -> stringResource(R.string.reason_spilled)
    Reason.MISCOUNT -> stringResource(R.string.reason_miscount)
    Reason.OTHER -> stringResource(R.string.reason_other)
    else -> code
}

@Composable
fun moveTypeLabel(type: String): String = stringResource(
    when (type) {
        StockMoveType.RESTOCK -> R.string.move_restock
        StockMoveType.SALE -> R.string.move_sale
        StockMoveType.VOID -> R.string.move_void
        else -> R.string.move_adjust
    },
)

fun IngredientEntity.dim(): Dimension = dimension()
