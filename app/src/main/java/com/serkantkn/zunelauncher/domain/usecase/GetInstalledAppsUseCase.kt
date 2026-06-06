package com.serkantkn.zunelauncher.domain.usecase

import com.serkantkn.zunelauncher.data.model.AppInfo
import com.serkantkn.zunelauncher.data.repository.AppRepository
import kotlinx.coroutines.flow.Flow

class GetInstalledAppsUseCase(private val repository: AppRepository) {
    operator fun invoke(): Flow<List<AppInfo>> = repository.getInstalledApps()
}
