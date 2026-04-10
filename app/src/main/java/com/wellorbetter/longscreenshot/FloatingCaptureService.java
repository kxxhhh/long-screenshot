package com.wellorbetter.longscreenshot;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

import com.wellorbetter.longscreenshot.compose.FrameComposerImpl;
import com.wellorbetter.longscreenshot.compose.FrameStoreImpl;
import com.wellorbetter.longscreenshot.compose.IFrameComposer;
import com.wellorbetter.longscreenshot.render.ResultRendererImpl;
import com.wellorbetter.longscreenshot.render.SaveCallback;

import java.io.InputStream;

/**
 * 通知栏控制的截图服务。
 *
 * 启动后立即开始截屏，通知栏显示"停止"按钮。
 * 用户滑动目标页面，点通知栏"停止"结束并合成长图。
 */
public class FloatingCaptureService extends Service {

    private static final String TAG = "LongSS";
    private static final String CHANNEL_ID = "long_screenshot_svc";
    private static final int NOTIFICATION_ID = 2001;
    private static final long CAPTURE_INTERVAL_MS = 500;
    private static final String ACTION_STOP = "com.wellorbetter.longscreenshot.STOP_CAPTURE";

    private IFrameComposer frameComposer;
    private HandlerThread captureThread;
    private Handler captureHandler;
    private Handler mainHandler;
    private PowerManager.WakeLock wakeLock;

    private volatile boolean capturing = false;
    private int frameCount = 0;
    private int statusBarHeight = 0;
    private int navBarHeight = 0;

    public static volatile String lastResultPath = null;
    public static volatile boolean hasResult = false;

    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Stop broadcast received");
            stopCapturing();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "Service onCreate");
        mainHandler = new Handler(Looper.getMainLooper());

        statusBarHeight = getResHeight("status_bar_height");
        navBarHeight = getResHeight("navigation_bar_height");
        Log.i(TAG, "insets top=" + statusBarHeight + " bottom=" + navBarHeight);

        captureThread = new HandlerThread("CaptureThread");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());

        frameComposer = new FrameComposerImpl(new FrameStoreImpl());

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LongScreenshot::Cap");
        wakeLock.acquire(10 * 60 * 1000L);

        // 注册停止广播
        IntentFilter filter = new IntentFilter(ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, filter);
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand, capturing=" + capturing);
        if (!capturing) {
            startForeground(NOTIFICATION_ID, buildNotification("准备中…3秒后开始截图"));
            // 3秒倒计时让用户切到目标App
            mainHandler.postDelayed(() -> {
                if (!capturing) {
                    startCapturing();
                }
            }, 3000);
        }
        return START_NOT_STICKY;
    }

    private void startCapturing() {
        capturing = true;
        frameCount = 0;
        frameComposer.reset();

        updateNotification("正在截图…滑动页面，下拉通知点「停止截图」结束");
        Log.i(TAG, "startCapturing");

        captureHandler.post(captureRunnable);
    }

    private final Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            if (!capturing) return;

            try {
                Bitmap frame = captureScreen();
                if (frame != null) {
                    int h = frame.getHeight();
                    int w = frame.getWidth();
                    Log.i(TAG, "frame#" + frameCount + " " + w + "x" + h);

                    int cropH = h - statusBarHeight - navBarHeight;
                    Bitmap toAppend;
                    if (cropH > 0 && statusBarHeight + cropH <= h) {
                        toAppend = Bitmap.createBitmap(frame, 0, statusBarHeight, w, cropH);
                        if (toAppend != frame) frame.recycle();
                    } else {
                        toAppend = frame;
                    }

                    frameComposer.appendFrame(toAppend, 0);
                    if (!toAppend.isRecycled()) toAppend.recycle();
                    frameCount++;

                    updateNotification("已捕获 " + frameCount + " 帧，下拉通知点「停止截图」结束");
                } else {
                    Log.w(TAG, "screencap returned null");
                }
            } catch (Exception e) {
                Log.e(TAG, "capture error", e);
            }

            if (capturing) {
                captureHandler.postDelayed(this, CAPTURE_INTERVAL_MS);
            }
        }
    };

    private Bitmap captureScreen() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "screencap -p"});
            InputStream is = p.getInputStream();
            Bitmap bmp = BitmapFactory.decodeStream(is);
            is.close();
            p.waitFor();
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "screencap failed", e);
            return null;
        }
    }

    private void stopCapturing() {
        if (!capturing) return;
        capturing = false;
        captureHandler.removeCallbacks(captureRunnable);

        Log.i(TAG, "stopCapturing, frames=" + frameCount);
        updateNotification("正在合成长图…");

        captureHandler.post(() -> {
            Bitmap result = frameComposer.compose();
            if (result == null) {
                Log.w(TAG, "compose returned null, frameCount=" + frameCount);
                mainHandler.post(() -> {
                    Toast.makeText(this, "合成失败（" + frameCount + " 帧）", Toast.LENGTH_LONG).show();
                    cleanup();
                });
                return;
            }

            Log.i(TAG, "Composed: " + result.getWidth() + "x" + result.getHeight());

            new ResultRendererImpl(this).saveToFile(result, new SaveCallback() {
                @Override
                public void onSaved(String filePath) {
                    Log.i(TAG, "Saved: " + filePath);
                    lastResultPath = filePath;
                    hasResult = true;
                    result.recycle();
                    frameComposer.reset();

                    Intent intent = new Intent(FloatingCaptureService.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    intent.putExtra("result_path", filePath);
                    startActivity(intent);
                    cleanup();
                }

                @Override
                public void onError(Throwable e) {
                    Log.e(TAG, "Save failed", e);
                    mainHandler.post(() -> Toast.makeText(FloatingCaptureService.this,
                            "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    result.recycle();
                    cleanup();
                }
            });
        });
    }

    private void cleanup() {
        mainHandler.post(() -> {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
            stopForeground(true);
            stopSelf();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        capturing = false;
        try { unregisterReceiver(stopReceiver); } catch (Exception ignored) {}
        if (captureThread != null) captureThread.quitSafely();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // --- Notification ---

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, "长截图服务", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("长截图运行时显示");
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String text) {
        // "停止截图"按钮
        Intent stopIntent = new Intent(ACTION_STOP);
        stopIntent.setPackage(getPackageName());
        PendingIntent stopPi = PendingIntent.getBroadcast(this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 点击通知回到 App
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent mainPi = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("LongScreenshot")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setOngoing(true)
                .setContentIntent(mainPi)
                .addAction(new Notification.Action.Builder(
                        null, "停止截图", stopPi).build());

        return builder.build();
    }

    private void updateNotification(String text) {
        getSystemService(NotificationManager.class)
                .notify(NOTIFICATION_ID, buildNotification(text));
    }

    private int getResHeight(String name) {
        int id = getResources().getIdentifier(name, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }
}
