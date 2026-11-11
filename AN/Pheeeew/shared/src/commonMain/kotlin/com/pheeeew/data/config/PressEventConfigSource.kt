package com.pheeeew.data.config

/** SDK adapters invoke each completion exactly once on the main thread, including failures. */
interface PressEventConfigSource {
    fun initialize(
        parameterKey: String,
        minimumFetchIntervalSeconds: Long,
        completion: (Boolean) -> Unit,
    )

    fun activatedBoolean(parameterKey: String): Boolean

    /** Success includes an unchanged cached value; it does not mean that activation changed a value. */
    fun fetchAndActivate(completion: (Boolean) -> Unit)
}
