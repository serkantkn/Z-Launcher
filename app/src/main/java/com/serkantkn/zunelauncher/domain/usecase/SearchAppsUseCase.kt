package com.serkantkn.zunelauncher.domain.usecase

import com.serkantkn.zunelauncher.data.model.AppInfo

class SearchAppsUseCase {
    operator fun invoke(apps: List<AppInfo>, query: String): List<AppInfo> {
        if (query.isBlank()) return apps
        return apps.filter { it.label.contains(query, ignoreCase = true) }
    }
}
