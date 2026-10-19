package com.pheeeew.core.di

import dev.zacsweers.metro.Qualifier

abstract class AppScope private constructor()

abstract class ActivityRetainedScope private constructor()

abstract class ViewControllerScope private constructor()

@Qualifier annotation class Bootstrap

@Qualifier annotation class SessionWork

@Qualifier annotation class PressWork

@Qualifier annotation class PlatformName

@Qualifier annotation class AppVersion

@Qualifier annotation class MapInitiallyEnabled

@Qualifier annotation class InvalidateMapSession

@Qualifier annotation class InvalidateRegions
