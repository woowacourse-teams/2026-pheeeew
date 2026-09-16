package com.pheeeew.fake

import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.model.sigh.SighBounds
import com.pheeeew.domain.model.sigh.SighPage
import com.pheeeew.domain.model.sigh.SighPin
import com.pheeeew.domain.repository.SighRepository

class FakeSighRepository : SighRepository {
    private var getMapSighsResult: Result<List<SighPin>>? = null
    private var createSighResult: Result<Sigh>? = null

    var getMapSighsCallCount: Int = 0
        private set
    var createSighCallCount: Int = 0
        private set

    private val _receivedCommands = mutableListOf<CreateSighCommand>()
    val receivedCommands: List<CreateSighCommand>
        get() = _receivedCommands

    fun setGetMapSighsSuccess(sighs: List<SighPin>) {
        getMapSighsResult = Result.success(sighs)
    }

    fun setGetMapSighsFailure(exception: ApiException) {
        getMapSighsResult = Result.failure(exception)
    }

    fun setCreateSighSuccess(sigh: Sigh) {
        createSighResult = Result.success(sigh)
    }

    fun setCreateSighFailure(exception: ApiException) {
        createSighResult = Result.failure(exception)
    }

    override suspend fun getFirstPage(bounds: SighBounds): SighPage = error("지원하지 않는 테스트 API입니다.")

    override suspend fun getNextPage(cursor: String): SighPage = error("지원하지 않는 테스트 API입니다.")

    override suspend fun getById(id: Long): Sigh = error("지원하지 않는 테스트 API입니다.")

    override suspend fun create(command: CreateSighCommand): Sigh {
        createSighCallCount += 1
        _receivedCommands += command

        return checkNotNull(createSighResult) {
            "create 결과를 먼저 설정해야 합니다."
        }.getOrThrow()
    }

    override suspend fun getMapSighs(bounds: SighBounds): List<SighPin> {
        getMapSighsCallCount += 1

        return checkNotNull(getMapSighsResult) {
            "getMapSighs 결과를 먼저 생성해야 합니다."
        }.getOrThrow().filter { sighPin ->
            sighPin.coordinate.longitude in bounds.minLongitude..bounds.maxLongitude &&
                sighPin.coordinate.latitude in bounds.minLatitude..bounds.maxLatitude
        }
    }
}
