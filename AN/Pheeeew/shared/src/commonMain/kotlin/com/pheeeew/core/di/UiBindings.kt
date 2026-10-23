package com.pheeeew.core.di

/** Provider composition only: each feature owns its own binding definitions. */
internal interface UiBindings :
    EmotionBindings,
    RankingBindings,
    ViewModelBindings
