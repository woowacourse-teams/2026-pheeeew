package com.pheeeew.domain.usecase

import com.pheeeew.domain.model.geo.Coordinate
import com.pheeeew.domain.model.sigh.CreateSighCommand
import com.pheeeew.domain.model.sigh.Sigh
import com.pheeeew.domain.repository.SighRepository
import com.pheeeew.domain.service.SighLocationObfuscator

class CreateSighUseCase(
    private val repository: SighRepository,
    private val locationObfuscator: SighLocationObfuscator,
) {
    fun prepare(
        requestId: String,
        coordinate: Coordinate,
        memo: String? = null,
    ): CreateSighCommand =
        CreateSighCommand(
            requestId = requestId,
            coordinate = locationObfuscator.obfuscate(coordinate),
            memo = memo,
        )

    suspend operator fun invoke(command: CreateSighCommand): Sigh = repository.create(command)
}
