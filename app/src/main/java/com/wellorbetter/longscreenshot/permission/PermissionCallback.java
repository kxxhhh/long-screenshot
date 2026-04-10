package com.wellorbetter.longscreenshot.permission;

/**
 * 权限请求结果回调。
 */
public interface PermissionCallback {

    /**
     * 权限已被用户授予。
     */
    void onGranted();

    /**
     * 权限被用户拒绝，或 Shizuku 服务不可用。
     */
    void onDenied();
}
