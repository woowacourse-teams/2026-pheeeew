package com.pheeeew.legacy.core.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidLocationPermissionControllerTest {
    @Test
    fun grantedPermissionWinsRegardlessOfRationaleState() {
        val status =
            androidLocationPermissionStatus(
                hasPermission = true,
                locationServicesEnabled = true,
                wasRequested = true,
                isActivityAttached = false,
                canExplainDenial = false,
            )

        assertEquals(LocationPermissionStatus.Granted, status)
    }

    @Test
    fun detachedActivityDoesNotProduceFalsePermanentDenial() {
        val status =
            androidLocationPermissionStatus(
                hasPermission = false,
                locationServicesEnabled = true,
                wasRequested = true,
                isActivityAttached = false,
                canExplainDenial = false,
            )

        assertEquals(LocationPermissionStatus.Denied, status)
    }

    @Test
    fun requestedPermissionWithoutRationaleIsPermanentlyDenied() {
        val status =
            androidLocationPermissionStatus(
                hasPermission = false,
                locationServicesEnabled = true,
                wasRequested = true,
                isActivityAttached = true,
                canExplainDenial = false,
            )

        assertEquals(LocationPermissionStatus.PermanentlyDenied, status)
    }

    @Test
    fun missingPermissionIsRequestableEvenWhenLocationServicesAreDisabled() {
        val status =
            androidLocationPermissionStatus(
                hasPermission = false,
                locationServicesEnabled = false,
                wasRequested = false,
                isActivityAttached = true,
                canExplainDenial = false,
            )

        assertEquals(LocationPermissionStatus.Denied, status)
    }

    @Test
    fun grantedPermissionWithDisabledLocationServicesReportsServicesDisabled() {
        val status =
            androidLocationPermissionStatus(
                hasPermission = true,
                locationServicesEnabled = false,
                wasRequested = true,
                isActivityAttached = true,
                canExplainDenial = false,
            )

        assertEquals(LocationPermissionStatus.ServicesDisabled, status)
    }
}
