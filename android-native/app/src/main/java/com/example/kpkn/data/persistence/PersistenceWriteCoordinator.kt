package com.example.kpkn.data.persistence

import kotlinx.coroutines.sync.Mutex

object PersistenceWriteCoordinator {
    val mutex = Mutex()
}
