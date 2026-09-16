package com.pheeeew.data.local.device

interface DeviceIdStorage {
    fun getOrCreate(): String
}
