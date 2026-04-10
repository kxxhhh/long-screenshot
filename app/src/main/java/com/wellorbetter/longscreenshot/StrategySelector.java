package com.wellorbetter.longscreenshot;

import android.content.Context;

import com.wellorbetter.longscreenshot.capture.RootCapture;

import rikka.shizuku.Shizuku;

/**
 * 自动检测并选择最优截图方案。
 *
 * <p>优先级：SHIZUKU > ROOT > MEDIA_PROJECTION
 * <ul>
 *   <li>SHIZUKU：体验最好，无弹窗，但需要 adb 用户预装 Shizuku</li>
 *   <li>ROOT：次之，su 权限直接截图</li>
 *   <li>MEDIA_PROJECTION：兜底，一次授权弹窗，覆盖全部设备</li>
 * </ul>
 */
public class StrategySelector {

    /**
     * 检测设备当前可用的最优方案。
     *
     * @param context 用于环境检测，传 applicationContext 即可
     * @return 推荐方案
     */
    public static CaptureStrategy detect(Context context) {
        // 1. 检测 Shizuku
        if (isShizukuAvailable()) {
            return CaptureStrategy.SHIZUKU;
        }
        // 2. 检测 Root
        if (RootCapture.isRootAvailable()) {
            return CaptureStrategy.ROOT;
        }
        // 3. 兜底 MediaProjection
        return CaptureStrategy.MEDIA_PROJECTION;
    }

    /**
     * 判断 Shizuku 是否已运行且本应用已获得授权。
     */
    private static boolean isShizukuAvailable() {
        try {
            // Shizuku.pingBinder() 在 Shizuku 未运行时会抛异常或返回 false
            if (!Shizuku.pingBinder()) return false;
            // 检查是否已授权（checkSelfPermission 不弹窗）
            return Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }
}
