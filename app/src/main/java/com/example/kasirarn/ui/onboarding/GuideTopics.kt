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

// Dibangkitkan oleh gen_guide.py; ubah isinya di skrip (tools/guide_tours.py), bukan di sini.

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
)

val GuideTopics = listOf(
    GuideTopic("sell", Icons.Outlined.PointOfSale, GuideSection.SELL, R.string.gt_sell_title, R.string.gt_sell_sum),
    GuideTopic("menu", Icons.Outlined.MenuBook, GuideSection.MANAGE, R.string.gt_menu_title, R.string.gt_menu_sum),
    GuideTopic("ingredients", Icons.Outlined.Inventory2, GuideSection.MANAGE, R.string.gt_ingredients_title, R.string.gt_ingredients_sum),
    GuideTopic("stock", Icons.Outlined.AddShoppingCart, GuideSection.MANAGE, R.string.gt_stock_title, R.string.gt_stock_sum),
    GuideTopic("categories", Icons.Outlined.Category, GuideSection.MANAGE, R.string.gt_categories_title, R.string.gt_categories_sum),
    GuideTopic("income", Icons.Outlined.BarChart, GuideSection.REPORTS, R.string.gt_income_title, R.string.gt_income_sum),
    GuideTopic("expenses", Icons.Outlined.Payments, GuideSection.REPORTS, R.string.gt_expenses_title, R.string.gt_expenses_sum),
    GuideTopic("notifications", Icons.Outlined.Notifications, GuideSection.SYSTEM, R.string.gt_notifications_title, R.string.gt_notifications_sum),
    GuideTopic("backup", Icons.Outlined.SettingsBackupRestore, GuideSection.SYSTEM, R.string.gt_backup_title, R.string.gt_backup_sum),
    GuideTopic("profile", Icons.Outlined.Storefront, GuideSection.SYSTEM, R.string.gt_profile_title, R.string.gt_profile_sum),
    GuideTopic("sample", Icons.Outlined.Science, GuideSection.SYSTEM, R.string.gt_sample_title, R.string.gt_sample_sum),
)

/** Langkah tutorial interaktif per fitur: layar tujuan, bagian yang disorot, dan teksnya. */
val GuideScripts: Map<String, List<CoachStep>> = mapOf(
    "sell" to listOf(
        CoachStep(CoachId.HOME_SEARCH, Routes.HOME, R.string.gs_sell_0_title, R.string.gs_sell_0_text),
        CoachStep(CoachId.HOME_CATEGORIES, Routes.HOME, R.string.gs_sell_1_title, R.string.gs_sell_1_text),
        CoachStep(CoachId.HOME_MENU, Routes.HOME, R.string.gs_sell_2_title, R.string.gs_sell_2_text),
        CoachStep(CoachId.HOME_CART, Routes.HOME, R.string.gs_sell_3_title, R.string.gs_sell_3_text),
        CoachStep(null, Routes.HOME, R.string.gs_sell_4_title, R.string.gs_sell_4_text),
        CoachStep(null, Routes.HOME, R.string.gs_sell_5_title, R.string.gs_sell_5_text),
    ),
    "menu" to listOf(
        CoachStep(CoachId.MANAGE_FAB, Routes.manage(ManageTab.MENU), R.string.gs_menu_0_title, R.string.gs_menu_0_text),
        CoachStep(CoachId.MENU_PHOTO, Routes.menuEdit(), R.string.gs_menu_1_title, R.string.gs_menu_1_text),
        CoachStep(CoachId.MENU_NAME, Routes.menuEdit(), R.string.gs_menu_2_title, R.string.gs_menu_2_text),
        CoachStep(CoachId.MENU_PRICE, Routes.menuEdit(), R.string.gs_menu_3_title, R.string.gs_menu_3_text),
        CoachStep(CoachId.MENU_CATEGORY, Routes.menuEdit(), R.string.gs_menu_4_title, R.string.gs_menu_4_text),
        CoachStep(CoachId.MENU_RECIPE, Routes.menuEdit(), R.string.gs_menu_5_title, R.string.gs_menu_5_text),
        CoachStep(CoachId.MENU_SAVE, Routes.menuEdit(), R.string.gs_menu_6_title, R.string.gs_menu_6_text),
    ),
    "ingredients" to listOf(
        CoachStep(CoachId.MANAGE_FAB, Routes.manage(ManageTab.INGREDIENT), R.string.gs_ingredients_0_title, R.string.gs_ingredients_0_text),
        CoachStep(CoachId.ING_NAME, Routes.ingredientEdit(), R.string.gs_ingredients_1_title, R.string.gs_ingredients_1_text),
        CoachStep(CoachId.ING_TYPE, Routes.ingredientEdit(), R.string.gs_ingredients_2_title, R.string.gs_ingredients_2_text),
        CoachStep(CoachId.ING_UNIT, Routes.ingredientEdit(), R.string.gs_ingredients_3_title, R.string.gs_ingredients_3_text),
        CoachStep(CoachId.ING_MIN, Routes.ingredientEdit(), R.string.gs_ingredients_4_title, R.string.gs_ingredients_4_text),
        CoachStep(CoachId.ING_INITIAL, Routes.ingredientEdit(), R.string.gs_ingredients_5_title, R.string.gs_ingredients_5_text),
        CoachStep(CoachId.ING_MEASURE, Routes.ingredientEdit(), R.string.gs_ingredients_6_title, R.string.gs_ingredients_6_text),
        CoachStep(CoachId.ING_SAVE, Routes.ingredientEdit(), R.string.gs_ingredients_7_title, R.string.gs_ingredients_7_text),
    ),
    "stock" to listOf(
        CoachStep(CoachId.TAB_MANAGE, Routes.HOME, R.string.gs_stock_0_title, R.string.gs_stock_0_text),
        CoachStep(null, Routes.manage(ManageTab.INGREDIENT), R.string.gs_stock_1_title, R.string.gs_stock_1_text),
        CoachStep(null, Routes.manage(ManageTab.INGREDIENT), R.string.gs_stock_2_title, R.string.gs_stock_2_text),
        CoachStep(null, Routes.manage(ManageTab.INGREDIENT), R.string.gs_stock_3_title, R.string.gs_stock_3_text),
    ),
    "categories" to listOf(
        CoachStep(CoachId.MANAGE_FAB, Routes.manage(ManageTab.CATEGORY), R.string.gs_categories_0_title, R.string.gs_categories_0_text),
        CoachStep(null, Routes.manage(ManageTab.CATEGORY), R.string.gs_categories_1_title, R.string.gs_categories_1_text),
        CoachStep(null, Routes.manage(ManageTab.CATEGORY), R.string.gs_categories_2_title, R.string.gs_categories_2_text),
    ),
    "income" to listOf(
        CoachStep(CoachId.REPORT_TABS, Routes.reports(ReportTab.INCOME), R.string.gs_income_0_title, R.string.gs_income_0_text),
        CoachStep(CoachId.REPORT_PERIOD, Routes.reports(ReportTab.INCOME), R.string.gs_income_1_title, R.string.gs_income_1_text),
        CoachStep(CoachId.REPORT_SUMMARY, Routes.reports(ReportTab.INCOME), R.string.gs_income_2_title, R.string.gs_income_2_text),
        CoachStep(null, Routes.reports(ReportTab.INCOME), R.string.gs_income_3_title, R.string.gs_income_3_text),
        CoachStep(null, Routes.reports(ReportTab.INCOME), R.string.gs_income_4_title, R.string.gs_income_4_text),
    ),
    "expenses" to listOf(
        CoachStep(CoachId.REPORT_TABS, Routes.reports(ReportTab.EXPENSES), R.string.gs_expenses_0_title, R.string.gs_expenses_0_text),
        CoachStep(CoachId.EXPENSE_FAB, Routes.reports(ReportTab.EXPENSES), R.string.gs_expenses_1_title, R.string.gs_expenses_1_text),
        CoachStep(CoachId.REPORT_SUMMARY, Routes.reports(ReportTab.EXPENSES), R.string.gs_expenses_2_title, R.string.gs_expenses_2_text),
        CoachStep(CoachId.EXPENSE_CATEGORIES, Routes.reports(ReportTab.EXPENSES), R.string.gs_expenses_3_title, R.string.gs_expenses_3_text),
        CoachStep(null, Routes.reports(ReportTab.EXPENSES), R.string.gs_expenses_4_title, R.string.gs_expenses_4_text),
    ),
    "notifications" to listOf(
        CoachStep(CoachId.HOME_BELL, Routes.HOME, R.string.gs_notifications_0_title, R.string.gs_notifications_0_text),
        CoachStep(CoachId.NOTIF_SETTINGS, Routes.NOTIFICATIONS, R.string.gs_notifications_1_title, R.string.gs_notifications_1_text),
        CoachStep(null, Routes.NOTIFICATIONS, R.string.gs_notifications_2_title, R.string.gs_notifications_2_text),
    ),
    "backup" to listOf(
        CoachStep(CoachId.MORE_BACKUP, Routes.MORE, R.string.gs_backup_0_title, R.string.gs_backup_0_text),
        CoachStep(CoachId.BACKUP_CREATE, Routes.BACKUP, R.string.gs_backup_1_title, R.string.gs_backup_1_text),
        CoachStep(CoachId.BACKUP_RESTORE, Routes.BACKUP, R.string.gs_backup_2_title, R.string.gs_backup_2_text),
        CoachStep(CoachId.BACKUP_REMINDER, Routes.BACKUP, R.string.gs_backup_3_title, R.string.gs_backup_3_text),
    ),
    "profile" to listOf(
        CoachStep(CoachId.MORE_PROFILE, Routes.MORE, R.string.gs_profile_0_title, R.string.gs_profile_0_text),
        CoachStep(CoachId.PROFILE_LOGO, Routes.PROFILE, R.string.gs_profile_1_title, R.string.gs_profile_1_text),
        CoachStep(CoachId.PROFILE_NAME, Routes.PROFILE, R.string.gs_profile_2_title, R.string.gs_profile_2_text),
        CoachStep(CoachId.PROFILE_SAVE, Routes.PROFILE, R.string.gs_profile_3_title, R.string.gs_profile_3_text),
        CoachStep(CoachId.MORE_THEME, Routes.MORE, R.string.gs_profile_4_title, R.string.gs_profile_4_text),
        CoachStep(CoachId.MORE_LANGUAGE, Routes.MORE, R.string.gs_profile_5_title, R.string.gs_profile_5_text),
    ),
    "sample" to listOf(
        CoachStep(CoachId.HOME_SAMPLE, Routes.HOME, R.string.gs_sample_0_title, R.string.gs_sample_0_text),
        CoachStep(CoachId.BACKUP_SAMPLE, Routes.BACKUP, R.string.gs_sample_1_title, R.string.gs_sample_1_text),
        CoachStep(CoachId.BACKUP_WIPE, Routes.BACKUP, R.string.gs_sample_2_title, R.string.gs_sample_2_text),
    ),
)
