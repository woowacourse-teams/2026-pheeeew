package com.pheeeew.core.di.report

import com.pheeeew.core.network.ApiClient
import com.pheeeew.data.remote.report.EmotionReportApi
import com.pheeeew.feature.screens.report.ReportDependencies
import com.pheeeew.feature.screens.report.adapter.ApiReportAction

fun createReportDependencies(apiClient: ApiClient): ReportDependencies =
    ReportDependencies(
        reportAction = ApiReportAction(EmotionReportApi(apiClient.requests)),
    )
