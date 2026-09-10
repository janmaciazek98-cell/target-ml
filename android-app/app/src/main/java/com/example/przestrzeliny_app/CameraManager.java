package com.example.przestrzeliny_app;

import android.util.Log;
import android.view.MotionEvent;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.TimeUnit;

public class CameraManager {

    private final AppCompatActivity activity;
    private final PreviewView viewFinder;
    private final SeekBar zoomSlider;
    private ImageCapture imageCapture;
    private Camera camera;

    private boolean isFrontCamera = false;
    private float minZoomRatio = 1.0f;
    private float maxZoomRatio = 5.0f;

    public CameraManager(AppCompatActivity activity, PreviewView viewFinder, SeekBar zoomSlider) {
        this.activity = activity;
        this.viewFinder = viewFinder;
        this.zoomSlider = zoomSlider;
    }

    public void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(activity);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                CameraSelector cameraSelector = isFrontCamera
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;

                ResolutionSelector resolutionSelector = new ResolutionSelector.Builder()
                        .setAspectRatioStrategy(new AspectRatioStrategy(
                                AspectRatio.RATIO_4_3,
                                AspectRatioStrategy.FALLBACK_RULE_AUTO
                        ))
                        .build();

                Preview preview = new Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setResolutionSelector(resolutionSelector)
                        .build();

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(activity, cameraSelector, preview, imageCapture);

                initZoomState();
                setupTapToFocus();

            } catch (Exception e) {
                Log.e("CameraX", "Błąd startu kamery: " + e.getMessage());
                Toast.makeText(activity, "Błąd startu kamery", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    private void initZoomState() {
        if (camera == null) return;

        camera.getCameraInfo().getZoomState().observe(activity, zoomState -> {
            if (zoomState != null) {
                minZoomRatio = zoomState.getMinZoomRatio();
                // Ograniczamy maksymalny zoom do 10x dla stabilności obrazu tarczy
                maxZoomRatio = Math.min(zoomState.getMaxZoomRatio(), 10.0f);

                updateZoomSliderProgress(zoomState.getZoomRatio());
            }
        });

        zoomSlider.setMax(100);
        zoomSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && camera != null) {
                    float targetRatio = minZoomRatio + (progress / 100.0f) * (maxZoomRatio - minZoomRatio);
                    camera.getCameraControl().setZoomRatio(targetRatio);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    public void setZoomRatio(float targetRatio) {
        if (camera == null) return;
        float clampedRatio = Math.max(minZoomRatio, Math.min(targetRatio, maxZoomRatio));
        camera.getCameraControl().setZoomRatio(clampedRatio);
        updateZoomSliderProgress(clampedRatio);
    }

    private void updateZoomSliderProgress(float currentRatio) {
        if (maxZoomRatio <= minZoomRatio) return;
        int progress = Math.round(((currentRatio - minZoomRatio) / (maxZoomRatio - minZoomRatio)) * 100f);
        zoomSlider.setProgress(Math.max(0, Math.min(100, progress)));
    }

    private void setupTapToFocus() {
        viewFinder.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                if (camera == null) return false;

                MeteringPointFactory factory = viewFinder.getMeteringPointFactory();
                MeteringPoint point = factory.createPoint(event.getX(), event.getY());
                FocusMeteringAction action = new FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
                )
                        .setAutoCancelDuration(3, TimeUnit.SECONDS)
                        .build();

                camera.getCameraControl().startFocusAndMetering(action);
                v.performClick();
                return true;
            }
            return true;
        });
    }

    public void switchCamera() {
        isFrontCamera = !isFrontCamera;
        startCamera();
    }

    public ImageCapture getImageCapture() {
        return imageCapture;
    }
}
