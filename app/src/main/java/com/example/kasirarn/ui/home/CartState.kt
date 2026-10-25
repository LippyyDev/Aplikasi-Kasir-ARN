package com.example.kasirarn.ui.home

import com.example.kasirarn.domain.CartLine
import com.example.kasirarn.domain.Discount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Keranjang aktif. Hidup selama proses berjalan sehingga tidak hilang saat berpindah layar. */
class CartState {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private val _discount = MutableStateFlow(Discount.NONE)
    val discount: StateFlow<Discount> = _discount.asStateFlow()

    fun quantityOf(menuId: Long): Int = _lines.value.firstOrNull { it.menuId == menuId }?.quantity ?: 0

    fun add(menuId: Long, by: Int = 1) {
        _lines.update { list ->
            val existing = list.firstOrNull { it.menuId == menuId }
            if (existing == null) list + CartLine(menuId, by) else list.map { if (it.menuId == menuId) it.copy(quantity = it.quantity + by) else it }
        }
    }

    fun setQuantity(menuId: Long, quantity: Int) {
        if (quantity <= 0) {
            remove(menuId)
        } else {
            _lines.update { list -> list.map { if (it.menuId == menuId) it.copy(quantity = quantity) else it } }
        }
    }

    fun remove(menuId: Long) {
        _lines.update { list -> list.filterNot { it.menuId == menuId } }
        if (_lines.value.isEmpty()) _discount.value = Discount.NONE
    }

    fun setNote(menuId: Long, note: String) {
        _lines.update { list ->
            val existing = list.any { it.menuId == menuId }
            if (existing) list.map { if (it.menuId == menuId) it.copy(note = note) else it } else list + CartLine(menuId, 1, note)
        }
    }

    fun setDiscount(discount: Discount) {
        _discount.value = discount
    }

    fun clear() {
        _lines.value = emptyList()
        _discount.value = Discount.NONE
    }
}
