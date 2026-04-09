package com.wanjian.longscreenshot;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.wanjian.longscreenshot.capture.IScreenCapture;
import com.wanjian.longscreenshot.capture.MediaProjectionCapture;
import com.wanjian.longscreenshot.capture.RootCapture;
import com.wanjian.longscreenshot.capture.SurfaceControlCapture;
import com.wanjian.longscreenshot.compose.FrameComposerImpl;
import com.wanjian.longscreenshot.compose.FrameStoreImpl;
import com.wanjian.longscreenshot.orchestrator.OrchestratorCallback;
import com.wanjian.longscreenshot.orchestrator.ScrollCaptureOrchestratorImpl;
import com.wanjian.longscreenshot.orchestrator.ScrollCaptureRequest;
import com.wanjian.longscreenshot.permission.IPermissionProvider;
import com.wanjian.longscreenshot.permission.ShizukuPermissionProvider;
import com.wanjian.longscreenshot.render.PreviewRendererImpl;
import com.wanjian.longscreenshot.render.ResultRendererImpl;
import com.wanjian.longscreenshot.render.SaveCallback;
import com.wanjian.longscreenshot.scroll.AccessibilityScrollController;
import com.wanjian.longscreenshot.scroll.IScrollController;
import com.wanjian.longscreenshot.scroll.InputManagerScrollController;
import com.wanjian.longscreenshot.scroll.LongScrollAccessibilityService;
import com.wanjian.longscreenshot.scroll.ScrollConfig;

/**
 * Composition Root：根据 StrategySelector 自动选择最优方案后组装所有模块。
 *
 * <p>三套方案优先级：SHIZUKU > ROOT > MEDIA_PROJECTION
 * <p>MediaProjection / Root 方案需要无障碍服务做滑动，首次使用时显示引导卡片。
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_MEDIA_PROJECTION = 1001;

    private ScrollCaptureOrchestratorImpl orchestrator;
    private IPermissionProvider permissionProvider;
    private IScreenCapture screenCapture;
    private IScrollController scrollController;

    private PreviewRendererImpl previewRenderer;
    private ResultRendererImpl resultRenderer;
    private FrameComposerImpl frameComposer;

    private ImageView imageView;
    private View cardAccessibility;
    private TextView tvStrategy;

    private CaptureStrategy strategy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        cardAccessibility = findViewById(R.id.cardAccessibility);
        tvStrategy = findViewById(R.id.tvStrategy);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnGoAccessibility = findViewById(R.id.btnGoAccessibility);

        // 跳转无障碍设置
        btnGoAccessibility.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // 初始化渲染和合成模块（策略无关）
        previewRenderer = new PreviewRendererImpl();
        previewRenderer.setListener(bitmap -> imageView.setImageBitmap(bitmap));
        resultRenderer = new ResultRendererImpl(this);
        resultRenderer.setListener(bitmap -> imageView.setImageBitmap(bitmap));
        FrameStoreImpl frameStore = new FrameStoreImpl();
        frameComposer = new FrameComposerImpl(frameStore);

        btnStart.setOnClickListener(v -> onStartClicked());
        btnStop.setOnClickListener(v -> { if (orchestrator != null) orchestrator.stop(); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到前台重新检测方案和无障碍状态（用户可能刚从设置页返回）
        strategy = StrategySelector.detect(this);
        updateStrategyUI();
    }

    /**
     * 更新顶部状态栏文字，决定是否显示无障碍引导卡片。
     */
    private void updateStrategyUI() {
        boolean needsAccessibility = needsAccessibilityService();
        boolean accessibilityOn = LongScrollAccessibilityService.getInstance() != null;

        switch (strategy) {
            case SHIZUKU:
                tvStrategy.setText("当前方案：Shizuku（无需无障碍服务）");
                cardAccessibility.setVisibility(View.GONE);
                break;
            case ROOT:
                tvStrategy.setText("当前方案：Root");
                showAccessibilityCardIfNeeded(accessibilityOn);
                break;
            case MEDIA_PROJECTION:
                tvStrategy.setText("当前方案：屏幕录制（首次需授权弹窗）");
                showAccessibilityCardIfNeeded(accessibilityOn);
                break;
        }
    }

    private void showAccessibilityCardIfNeeded(boolean accessibilityOn) {
        if (accessibilityOn) {
            cardAccessibility.setVisibility(View.GONE);
            tvStrategy.setText(tvStrategy.getText() + " · 无障碍 ✓");
        } else {
            cardAccessibility.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 当前方案是否依赖无障碍服务做滑动。
     */
    private boolean needsAccessibilityService() {
        return strategy == CaptureStrategy.MEDIA_PROJECTION || strategy == CaptureStrategy.ROOT;
    }

    private void onStartClicked() {
        // 需要无障碍但未开启 → 提示用户，不启动截图
        if (needsAccessibilityService() && LongScrollAccessibilityService.getInstance() == null) {
            cardAccessibility.setVisibility(View.VISIBLE);
            Toast.makeText(this, "请先按上方步骤开启无障碍服务", Toast.LENGTH_LONG).show();
            return;
        }

        switch (strategy) {
            case SHIZUKU:
                buildShizukuPipelineAndStart();
                break;
            case ROOT:
                buildRootPipelineAndStart();
                break;
            case MEDIA_PROJECTION:
                requestMediaProjectionAndStart();
                break;
        }
    }

    // ─────────────────────────────────────────
    // 方案 1：Shizuku
    // ─────────────────────────────────────────

    private void buildShizukuPipelineAndStart() {
        ShizukuPermissionProvider shizukuProvider = new ShizukuPermissionProvider();
        shizukuProvider.request(new com.wanjian.longscreenshot.permission.PermissionCallback() {
            @Override
            public void onGranted() {
                screenCapture = new SurfaceControlCapture(
                        shizukuProvider,
                        (android.view.WindowManager) getSystemService(WINDOW_SERVICE));
                scrollController = new InputManagerScrollController(shizukuProvider, screenCapture);
                permissionProvider = shizukuProvider;
                buildOrchestratorAndStart();
            }

            @Override
            public void onDenied() {
                Toast.makeText(MainActivity.this, "Shizuku 授权被拒绝", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────
    // 方案 2：Root
    // ─────────────────────────────────────────

    private void buildRootPipelineAndStart() {
        screenCapture = new RootCapture(getCacheDir());
        scrollController = new AccessibilityScrollController();
        permissionProvider = null;
        buildOrchestratorAndStart();
    }

    // ─────────────────────────────────────────
    // 方案 3：MediaProjection
    // ─────────────────────────────────────────

    private void requestMediaProjectionAndStart() {
        startActivityForResult(
                MediaProjectionCapture.createScreenCaptureIntent(this), REQ_MEDIA_PROJECTION);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_MEDIA_PROJECTION) {
            MediaProjectionCapture mpCapture =
                    MediaProjectionCapture.fromActivityResult(this, resultCode, data);
            if (mpCapture == null) {
                Toast.makeText(this, "屏幕录制授权被拒绝", Toast.LENGTH_SHORT).show();
                return;
            }
            screenCapture = mpCapture;
            scrollController = new AccessibilityScrollController();
            permissionProvider = null;
            buildOrchestratorAndStart();
        }
    }

    // ─────────────────────────────────────────
    // 公共：组装 Orchestrator 并启动
    // ─────────────────────────────────────────

    private void buildOrchestratorAndStart() {
        orchestrator = new ScrollCaptureOrchestratorImpl(
                permissionProvider,
                screenCapture,
                scrollController,
                frameComposer,
                previewRenderer);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        ScrollConfig scrollConfig = new ScrollConfig(
                300, 300, screenWidth / 2f, screenHeight * 0.7f);
        Rect captureInsets = new Rect(0, getStatusBarHeight(), 0, getNavigationBarHeight());

        ScrollCaptureRequest request = new ScrollCaptureRequest(
                scrollConfig,
                captureInsets,
                new OrchestratorCallback() {
                    @Override
                    public void onProgress(Bitmap preview, int frameCount) {
                        // 实时预览由 previewRenderer listener 更新
                    }

                    @Override
                    public void onComplete(Bitmap result) {
                        resultRenderer.showResult(result);
                        resultRenderer.saveToFile(result, new SaveCallback() {
                            @Override
                            public void onSaved(String filePath) {
                                Toast.makeText(MainActivity.this,
                                        "已保存：" + filePath, Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onError(Throwable e) {
                                Toast.makeText(MainActivity.this,
                                        "保存失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(MainActivity.this,
                                "截图失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
        orchestrator.start(request);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (orchestrator != null) orchestrator.stop();
        if (screenCapture != null) screenCapture.release();
        if (permissionProvider != null) permissionProvider.release();
    }

    private int getStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int getNavigationBarHeight() {
        int id = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }
}
