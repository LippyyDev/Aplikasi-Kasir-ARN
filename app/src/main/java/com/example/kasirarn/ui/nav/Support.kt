package com.example.kasirarn.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kasirarn.AppContainer
import com.example.kasirarn.container

object Routes {
    // Lima tujuan utama (bar bawah / rail)
    const val DASHBOARD = "dashboard"
    const val HOME = "home"
    const val MANAGE = "manage?tab={tab}"
    const val REPORTS = "reports?tab={tab}"
    const val MORE = "more"

    // Layar turunan
    const val PAYMENT = "payment"
    const val SUCCESS = "success/{id}"
    const val PROFILE = "profile"
    const val NOTIFICATIONS = "notifications"
    const val MENU_EDIT = "menu_edit?id={id}"
    const val INGREDIENT_EDIT = "ingredient_edit?id={id}"
    const val INGREDIENT_DETAIL = "ingredient/{id}"
    const val TX_DETAIL = "transaction/{id}"
    const val EXPENSE_CATEGORIES = "expense_categories"
    const val BACKUP = "backup"
    const val ABOUT = "about"
    const val GUIDE = "guide"

    fun success(id: Long) = "success/$id"
    fun menuEdit(id: Long? = null) = if (id == null) "menu_edit" else "menu_edit?id=$id"
    fun ingredientEdit(id: Long? = null) = if (id == null) "ingredient_edit" else "ingredient_edit?id=$id"
    fun ingredient(id: Long) = "ingredient/$id"
    fun transaction(id: Long) = "transaction/$id"
    fun manage(tab: Int = ManageTab.MENU) = "manage?tab=$tab"
    fun reports(tab: Int = ReportTab.INCOME) = "reports?tab=$tab"
}

object ManageTab {
    const val MENU = 0
    const val INGREDIENT = 1
    const val CATEGORY = 2
}

object ReportTab {
    const val INCOME = 0
    const val EXPENSES = 1
}

/** Membuat ViewModel dengan akses ke [AppContainer]. [key] membedakan instans untuk argumen berbeda. */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    crossinline create: (AppContainer) -> VM,
): VM {
    val container = LocalContext.current.container
    return viewModel(
        key = key,
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = create(container) as T
        },
    )
}
