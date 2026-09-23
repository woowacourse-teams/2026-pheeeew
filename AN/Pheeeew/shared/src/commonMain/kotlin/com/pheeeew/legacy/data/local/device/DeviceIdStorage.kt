package com.pheeeew.legacy.data.local.device

interface DeviceIdStorage {
    fun getOrCreate(): String
}
