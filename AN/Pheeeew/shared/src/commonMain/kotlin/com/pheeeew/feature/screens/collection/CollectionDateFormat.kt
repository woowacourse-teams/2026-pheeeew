package com.pheeeew.feature.screens.collection

internal fun formatCollectionCardDate(date: String): String {
    if (date.length != 10 || date[4] != '-' || date[7] != '-') return date
    return date.replace('-', '.')
}
