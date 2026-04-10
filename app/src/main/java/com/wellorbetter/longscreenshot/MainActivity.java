package com.wellorbetter.longscreenshot;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

/**
 * 主界面：启动悬浮窗截图服务 + 展示截图结果。
 */
public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private TextView tvEmptyHint;
    private Chip tvStrategy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        tvEmptyHint = findViewById(R.id.tvEmptyHint);
        tvStrategy = findViewById(R.id.tvStrategy);
        MaterialButton btnStart = findViewById(R.id.btnStart);
        MaterialButton btnStop = findViewById(R.id.btnStop);

        // 隐藏无障碍卡片（新流程不需要）
        View cardAccessibility = findViewById(R.id.cardAccessibility);
        if (cardAccessibility != null) cardAccessibility.setVisibility(View.GONE);

        btnStart.setText("开始长截图");
        btnStop.setText("查看相册");

        btnStart.setOnClickListener(v -> startFloatingCapture());
        btnStop.setOnClickListener(v -> openGallery());

        // 检测方案
        CaptureStrategy strategy = StrategySelector.detect(this);
        switch (strategy) {
            case SHIZUKU:
                tvStrategy.setText("方案：Shizuku");
                break;
            case ROOT:
                tvStrategy.setText("方案：Root（推荐）");
                break;
            case MEDIA_PROJECTION:
                tvStrategy.setText("方案：屏幕录制");
                break;
        }

        // 检查是否有截图结果传回
        handleResult(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleResult(intent);
    }

    private void handleResult(Intent intent) {
        if (intent == null) return;
        String path = intent.getStringExtra("result_path");
        if (path != null) {
            imageView.setImageBitmap(BitmapFactory.decodeFile(path));
            tvEmptyHint.setVisibility(View.GONE);
            Toast.makeText(this, "已保存：" + path, Toast.LENGTH_LONG).show();
        }
    }

    private void startFloatingCapture() {
        Intent serviceIntent = new Intent(this, FloatingCaptureService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "3秒后开始截图，请切到目标App", Toast.LENGTH_LONG).show();
        // 最小化自己
        moveTaskToBack(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void openGallery() {
        // 直接打开 Pictures/LongScreenshot 目录
        java.io.File dir = new java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES), "LongScreenshot");
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivity(Intent.createChooser(intent, "查看长截图"));
        } catch (Exception e) {
            Toast.makeText(this, "无法打开相册", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检查是否有 static 传回的结果
        if (FloatingCaptureService.hasResult && FloatingCaptureService.lastResultPath != null) {
            imageView.setImageBitmap(
                    BitmapFactory.decodeFile(FloatingCaptureService.lastResultPath));
            tvEmptyHint.setVisibility(View.GONE);
            FloatingCaptureService.hasResult = false;
        }
    }
}
