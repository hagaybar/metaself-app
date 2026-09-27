package com.metaself.app.ui.screen.settings

import androidx.annotation.StringRes
import com.metaself.app.R

/**
 * The six pages of Settings (D79), in the order the index lists them. [path] is the last part of
 * the page's route; [title] is its row on the index and its title bar.
 */
enum class SettingsPage(val path: String, @StringRes val title: Int) {
    EATING("eating", R.string.settings_page_eating),
    MOVEMENT("movement", R.string.settings_page_movement),
    BACKUPS("backups", R.string.settings_page_backups),
    AI("ai", R.string.settings_page_ai),
    FOOD_DATABASE("food-database", R.string.settings_page_food),
    PROBLEMS("problems", R.string.settings_problems_title),
}
