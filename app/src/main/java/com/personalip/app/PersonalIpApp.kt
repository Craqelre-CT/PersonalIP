package com.personalip.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * 应用入口。
 * - @HiltAndroidApp 触发 Hilt 依赖注入代码生成。
 * - 实现 Configuration.Provider 让 WorkManager 使用 HiltWorkerFactory，
 *   这样带有 @HiltWorker 注解的 Worker 也能被注入。
 */
@HiltAndroidApp
class PersonalIpApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    // 旧版 WorkManagerInitializer 已在 AndroidManifest 中移除，由这里提供自定义配置。
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
