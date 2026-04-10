package com.wellorbetter.longscreenshot.permission;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * 基于 Shizuku 的权限提供者实现。
 *
 * <p>使用前需确保设备已启动 Shizuku 服务（adb / root 模式均可）。
 * 在 Activity/Application 生命周期结束时应调用 {@link #release()} 解除监听。
 */
public class ShizukuPermissionProvider implements IPermissionProvider {

    private static final int REQUEST_CODE = 100;

    private PermissionCallback pendingCallback;

    /**
     * Shizuku 权限结果监听器。在构造函数中注册，在 release() 中注销。
     */
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            (requestCode, grantResult) -> {
                if (requestCode != REQUEST_CODE || pendingCallback == null) return;
                PermissionCallback cb = pendingCallback;
                pendingCallback = null;
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    cb.onGranted();
                } else {
                    cb.onDenied();
                }
            };

    public ShizukuPermissionProvider() {
        // TODO: Shizuku.addRequestPermissionResultListener 注册监听
        //   调用 Shizuku.addRequestPermissionResultListener(permissionResultListener)
        //   以便接收 requestPermission 的异步结果。
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    @Override
    public void request(PermissionCallback callback) {
        if (isGranted()) {
            callback.onGranted();
            return;
        }
        if (!Shizuku.pingBinder()) {
            callback.onDenied();
            return;
        }
        pendingCallback = callback;
        // TODO: Shizuku.requestPermission(requestCode) 请求授权
        //   调用 Shizuku.requestPermission(REQUEST_CODE) 触发系统弹窗，
        //   用户操作结果会通过 permissionResultListener 回调。
        Shizuku.requestPermission(REQUEST_CODE);
    }

    @Override
    public boolean isGranted() {
        try {
            return Shizuku.pingBinder()
                    && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public android.os.IBinder getSystemService(String serviceName) {
        if (!isGranted()) return null;
        try {
            // TODO: ShizukuBinderWrapper 包装 IBinder 返回系统服务
            //   使用 SystemServiceHelper.getSystemService(serviceName) 拿到原始 IBinder，
            //   再用 new ShizukuBinderWrapper(binder) 包装，使其可在 App 进程跨进程调用。
            //   调用方再自行调用 IInputManager.Stub.asInterface(binder) 完成类型转换。
            android.os.IBinder rawBinder = SystemServiceHelper.getSystemService(serviceName);
            if (rawBinder == null) return null;
            return new ShizukuBinderWrapper(rawBinder);
        } catch (Exception e) {
            return null;
        }
    }

    /** 注销 Shizuku 监听器，需在 Activity/Application 销毁时调用。 */
    public void release() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }
}
