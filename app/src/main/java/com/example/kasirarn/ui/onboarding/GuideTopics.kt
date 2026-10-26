package com.example.kasirarn.ui.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddShoppingCart
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.kasirarn.R
import com.example.kasirarn.ui.nav.ManageTab
import com.example.kasirarn.ui.nav.ReportTab
import com.example.kasirarn.ui.nav.Routes

// Dibangkitkan oleh gen_guide.py; ubah isinya di skrip, bukan di sini.

enum class GuideSection(val labelRes: Int) {
    SELL(R.string.guide_sec_sell),
    MANAGE(R.string.guide_sec_manage),
    REPORTS(R.string.guide_sec_reports),
    SYSTEM(R.string.guide_sec_system),
}

class GuideTopic(
    val id: String,
    val icon: ImageVector,
    val section: GuideSection,
    val titleRes: Int,
    val summaryRes: Int,
    val stepsRes: Int,
    val tipsRes: Int,
    val openRoute: String,
)

val GuideTopics = listOf(
    GuideTopic("sell", Icons.Outlined.PointOfSale, GuideSection.SELL, R.string.gt_sell_title, R.string.gt_sell_sum, R.array.gt_sell_steps, R.array.gt_sell_tips, Routes.HOME),
    GuideTopic("menu", Icons.Outlined.MenuBook, GuideSection.MANAGE, R.string.gt_menu_title, R.string.gt_menu_sum, R.array.gt_menu_steps, R.array.gt_menu_tips, Routes.manage(ManageTab.MENU)),
    GuideTopic("ingredients", Icons.Outlined.Inventory2, GuideSection.MANAGE, R.string.gt_ingredients_title, R.string.gt_ingredients_sum, R.array.gt_ingredients_steps, R.array.gt_ingredients_tips, Routes.manage(ManageTab.INGREDIENT)),
    GuideTopic("stock", Icons.Outlined.AddShoppingCart, GuideSection.MANAGE, R.string.gt_stock_title, R.string.gt_stock_sum, R.array.gt_stock_steps, R.array.gt_stock_tips, Routes.manage(ManageTab.INGREDIENT)),
    GuideTopic("categories", Icons.Outlined.Category, GuideSection.MANAGE, R.string.gt_categories_title, R.string.gt_categories_sum, R.array.gt_categories_steps, R.array.gt_categories_tips, Routes.manage(ManageTab.CATEGORY)),
    GuideTopic("income", Icons.Outlined.BarChart, GuideSection.REPORTS, R.string.gt_income_title, R.string.gt_income_sum, R.array.gt_income_steps, R.array.gt_income_tips, Routes.reports(ReportTab.INCOME)),
    GuideTopic("expenses", Icons.Outlined.Payments, GuideSection.REPORTS, R.string.gt_expenses_title, R.string.gt_expenses_sum, R.array.gt_expenses_steps, R.array.gt_expenses_tips, Routes.reports(ReportTab.EXPENSES)),
    GuideTopic("notifications", Icons.Outlined.Notifications, GuideSection.SYSTEM, R.string.gt_notifications_title, R.string.gt_notifications_sum, R.array.gt_notifications_steps, R.array.gt_notifications_tips, Routes.NOTIFICATIONS),
    GuideTopic("backup", Icons.Outlined.SettingsBackupRestore, GuideSection.SYSTEM, R.string.gt_backup_title, R.string.gt_backup_sum, R.array.gt_backup_steps, R.array.gt_backup_tips, Routes.BACKUP),
    GuideTopic("profile", Icons.Outlined.Storefront, GuideSection.SYSTEM, R.string.gt_profile_title, R.string.gt_profile_sum, R.array.gt_profile_steps, R.array.gt_profile_tips, Routes.PROFILE),
    GuideTopic("sample", Icons.Outlined.Science, GuideSection.SYSTEM, R.string.gt_sample_title, R.string.gt_sample_sum, R.array.gt_sample_steps, R.array.gt_sample_tips, Routes.BACKUP),
)
