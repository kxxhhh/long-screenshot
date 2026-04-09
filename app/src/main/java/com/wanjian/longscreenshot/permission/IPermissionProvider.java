package com.wanjian.longscreenshot.permission;

import android.os.IBinder;

/**
 * 权限提供者接口，抽象不同授权方式（Shizuku / Root / 无障碍等）的差异。
 */
public interface IPermissionProvider {

    /**
     * 向用户请求权限。结果通过 {@link PermissionCallback} 异步回调。
     *
     * @param callback 授权结果回调。
     */
    void request(PermissionCallback callback);

    /**
     * 检查当前是否已持有所需权限。
     *
     * @return true 表示已授权，可直接调用系统服务；false 表示尚未授权。
     */
    boolean isGranted();

    /**
     * 通过 Shizuku 获取系统服务的原始 IBinder。
     * 调用方需自行调用对应 AIDL Stub.asInterface(binder) 完成转换。
     *
     * @param serviceName 服务名称，例如 {@code Context.INPUT_SERVICE}。
     * @return 经 ShizukuBinderWrapper 包装的 IBinder，未授权或服务不可用时返回 null。
     */
    IBinder getSystemService(String serviceName);

    /**
     * 释放资源，注销 Shizuku 监听器。应在 Activity/Application 销毁时调用。
     */
    void release();
}
