package com.personalip.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.personalip.app.ui.LocalRootState
import com.personalip.app.ui.RootStateHost
import com.personalip.app.ui.navigation.PersonalIpNavHost
import com.personalip.app.ui.theme.PersonalIpTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * 单 Activity 入口。所有页面通过 Compose Navigation 管理。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PersonalIpTheme {
                // RootStateHost 提供「是否已授权根目录」的全局状态，决定显示首页或引导授权页。
                RootStateHost {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PersonalIpNavHost()
                    }
                }
            }
        }
    }
}
