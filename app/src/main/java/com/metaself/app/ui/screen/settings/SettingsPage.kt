package com.metaself.app.ui.screen.settings

import androidx.annotation.StringRes
import com.metaself.app.R

/**
 * The pages of Settings (D79): a testing tool (D106), then About this app last (D134). In the order the index
 * lists them, under its first row, Your numbers (D133), which is not a page here but the root's cover. [path]
 * is the last part of the page's route; [title] is its row on the index and its title bar.
 */
enum class SettingsPage(val path: String, @StringRes val title: Int) {
    EATING("eating", R.string.settings_page_eating),
    MOVEMENT("movement", R.string.settings_page_movement),
    BACKUPS("backups", R.string.settings_page_backups),
    AI("ai", R.string.settings_page_ai),
    FOOD_DATABASE("food-database", R.string.settings_page_food),
    PROBLEMS("problems", R.string.settings_problems_title),
    TRAINER_INSTRUCTIONS("trainer-instructions", R.string.settings_page_trainer_instructions),
    ABOUT("about", R.string.settings_page_about),
}
