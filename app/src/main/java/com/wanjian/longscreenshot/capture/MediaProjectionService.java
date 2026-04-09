package com.wanjian.longscreenshot.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * 前台服务。Android 10+ 使用 MediaProjection 截图必须绑定前台服务。
 * 由 MediaProjectionCapture 在开始截图前启动，截图完成后停止。
 */
public class MediaProjectionService extends Service {

    private static final String CHANNEL_ID = "long_screenshot";
    private static final int NOTIFICATION_ID = 1001;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("长截图进行中")
                .setContentText("正在截取屏幕内容")
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .build();
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "长截图", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("长截图截取屏幕时显示");
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
