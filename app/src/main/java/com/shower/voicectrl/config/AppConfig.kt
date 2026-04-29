package com.shower.voicectrl.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shower.voicectrl.accessibility.SupportedApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 应用配置管理，使用 DataStore 持久化用户偏好。
 *
 * 配置项：
 * - 手势坐标（centerXPct, swipeTopYPct, swipeBottomYPct, tapYPct）
 * - 超时时长（idleTimeoutMinutes）
 * - 启用的目标 App 列表（enabledAppPackages）
 */
class AppConfig(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_config")

    companion object {
        // 手势坐标配置（百分比）
        private val CENTER_X_PCT = floatPreferencesKey("center_x_pct")
        private val SWIPE_TOP_Y_PCT = floatPreferencesKey("swipe_top_y_pct")
        private val SWIPE_BOTTOM_Y_PCT = floatPreferencesKey("swipe_bottom_y_pct")
        private val TAP_Y_PCT = floatPreferencesKey("tap_y_pct")

        // 超时配置（分钟）
        private val IDLE_TIMEOUT_MINUTES = longPreferencesKey("idle_timeout_minutes")

        // 启用的目标 App 包名集合
        private val ENABLED_APP_PACKAGES = stringSetPreferencesKey("enabled_app_packages")

        // 默认值
        const val DEFAULT_CENTER_X_PCT = 0.5f
        const val DEFAULT_SWIPE_TOP_Y_PCT = 0.25f
        const val DEFAULT_SWIPE_BOTTOM_Y_PCT = 0.75f
        const val DEFAULT_TAP_Y_PCT = 0.5f
        const val DEFAULT_IDLE_TIMEOUT_MINUTES = 30L
    }

    /**
     * 手势配置数据类
     */
    data class GestureCoordinates(
        val centerXPct: Float = DEFAULT_CENTER_X_PCT,
        val swipeTopYPct: Float = DEFAULT_SWIPE_TOP_Y_PCT,
        val swipeBottomYPct: Float = DEFAULT_SWIPE_BOTTOM_Y_PCT,
        val tapYPct: Float = DEFAULT_TAP_Y_PCT
    )

    // 读取手势坐标配置
    val gestureCoordinates: Flow<GestureCoordinates> = context.dataStore.data.map { prefs ->
        GestureCoordinates(
            centerXPct = prefs[CENTER_X_PCT] ?: DEFAULT_CENTER_X_PCT,
            swipeTopYPct = prefs[SWIPE_TOP_Y_PCT] ?: DEFAULT_SWIPE_TOP_Y_PCT,
            swipeBottomYPct = prefs[SWIPE_BOTTOM_Y_PCT] ?: DEFAULT_SWIPE_BOTTOM_Y_PCT,
            tapYPct = prefs[TAP_Y_PCT] ?: DEFAULT_TAP_Y_PCT
        )
    }

    // 读取超时配置
    val idleTimeoutMinutes: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[IDLE_TIMEOUT_MINUTES] ?: DEFAULT_IDLE_TIMEOUT_MINUTES
    }

    // 读取启用的 App 包名集合（默认为所有支持的 App）
    val enabledAppPackages: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[ENABLED_APP_PACKAGES] ?: SupportedApp.supportedPackages()
    }

    // 更新手势坐标
    suspend fun updateGestureCoordinates(coordinates: GestureCoordinates) {
        context.dataStore.edit { prefs ->
            prefs[CENTER_X_PCT] = coordinates.centerXPct
            prefs[SWIPE_TOP_Y_PCT] = coordinates.swipeTopYPct
            prefs[SWIPE_BOTTOM_Y_PCT] = coordinates.swipeBottomYPct
            prefs[TAP_Y_PCT] = coordinates.tapYPct
        }
    }

    // 更新超时时长
    suspend fun updateIdleTimeout(minutes: Long) {
        context.dataStore.edit { prefs ->
            prefs[IDLE_TIMEOUT_MINUTES] = minutes
        }
    }

    // 更新启用的 App 包名集合
    suspend fun updateEnabledAppPackages(packages: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[ENABLED_APP_PACKAGES] = packages
        }
    }
}
