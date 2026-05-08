package com.shower.voicectrl.accessibility

/**
 * 支持的短视频 App 定义。
 *
 * 每个 App 包含：
 * - packageName: 应用包名
 * - displayName: 显示名称
 * - gestureConfig: 专属手势配置（可选，默认使用通用配置）
 */
data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val gestureConfig: GestureConfig? = null
) {
    companion object {
        /**
         * 默认支持的短视频 App 列表。
         * UI 交互模式（上滑切下一条 / 下滑回上一条 / 点中心暂停）一致的 App 可加入。
         */
        val DEFAULT_APPS = listOf(
            SupportedApp(
                packageName = "com.ss.android.ugc.aweme",
                displayName = "抖音"
            ),
            SupportedApp(
                packageName = "com.ss.android.ugc.aweme.lite",
                displayName = "抖音极速版"
            ),
            SupportedApp(
                packageName = "com.smile.gifmaker",
                displayName = "快手"
            ),
            SupportedApp(
                packageName = "com.kuaishou.nebula",
                displayName = "快手极速版"
            )
        )

        /**
         * 根据包名查找支持的 App
         */
        fun findByPackage(packageName: String): SupportedApp? {
            return DEFAULT_APPS.find { it.packageName == packageName }
        }

        /**
         * 检查包名是否被支持
         */
        fun isSupported(packageName: String): Boolean {
            return DEFAULT_APPS.any { it.packageName == packageName }
        }

        /**
         * 检查包名是否被支持且当前启用。
         */
        fun isEnabled(packageName: String, enabledPackages: Set<String>): Boolean {
            return isSupported(packageName) && packageName in enabledPackages
        }

        /**
         * 获取所有支持的包名集合
         */
        fun supportedPackages(): Set<String> {
            return DEFAULT_APPS.map { it.packageName }.toSet()
        }
    }
}
