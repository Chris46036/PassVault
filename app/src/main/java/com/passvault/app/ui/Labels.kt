package com.passvault.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.passvault.app.R
import com.passvault.app.data.Categories
import com.passvault.app.data.EntryType

@Composable
fun categoryLabel(key: String): String = stringResource(
    when (key) {
        Categories.LOGIN -> R.string.cat_accounts
        Categories.SOCIAL -> R.string.cat_social
        Categories.BANK -> R.string.cat_bank
        Categories.EMAIL -> R.string.cat_email
        Categories.WORK -> R.string.cat_work
        Categories.WIFI -> R.string.cat_wifi
        else -> R.string.cat_other
    }
)

@Composable
fun typeLabel(key: String): String = stringResource(
    when (key) {
        EntryType.CARD -> R.string.type_card
        EntryType.NOTE -> R.string.type_note
        EntryType.IDENTITY -> R.string.type_identity
        else -> R.string.type_login
    }
)
