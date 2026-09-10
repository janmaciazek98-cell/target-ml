package com.example.przestrzeliny_app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private ImageButton btnCapture;
    private Button btnGallery;
    private SeekBar zoomSlider;
    private Button btnSwitchCamera;
    private TextView txtResult;
    private ImageView imageViewResult;

    private SwitchMaterial switchEnhance;
    private LinearLayout enhanceSlidersLayout;
    private SeekBar contrastSlider;
    private SeekBar brightnessSlider;
    private TextView txtContrast;
    private TextView txtBrightness;

    private CameraManager cameraManager;
    private PermissionHelper permissionHelper;
    private ExecutorService cameraExecutor;
    private YoloDetector yoloDetector;

    private ActivityResultLauncher<String> galleryLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (OpenCVLoader.initDebug()) Log.d("OPENCV", "OpenCV załadowane");

        viewFinder = findViewById(R.id.viewFinder);
        btnCapture = findViewById(R.id.btnCapture);
        btnGallery = findViewById(R.id.btnGallery);
        zoomSlider = findViewById(R.id.zoomSlider);
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera);
        txtResult = findViewById(R.id.txtResult);
        imageViewResult = findViewById(R.id.imageViewResult);

        switchEnhance = findViewById(R.id.switchEnhance);
        enhanceSlidersLayout = findViewById(R.id.enhanceSlidersLayout);
        contrastSlider = findViewById(R.id.contrastSlider);
        brightnessSlider = findViewById(R.id.brightnessSlider);
        txtContrast = findViewById(R.id.txtContrast);
        txtBrightness = findViewById(R.id.txtBrightness);

        setupEnhanceUI();

        imageViewResult.setOnClickListener(v -> imageViewResult.setVisibility(View.GONE));

        yoloDetector = new YoloDetector();
        yoloDetector.initModel(getAssets(), "model.ncnn.param", "model.ncnn.bin");

        cameraManager = new CameraManager(this, viewFinder, zoomSlider);
        btnSwitchCamera.setOnClickListener(v -> cameraManager.switchCamera());

        Button btnZoom05 = findViewById(R.id.btnZoom05);
        Button btnZoom1 = findViewById(R.id.btnZoom1);
        Button btnZoom2 = findViewById(R.id.btnZoom2);

        btnZoom05.setOnClickListener(v -> cameraManager.setZoomRatio(0.5f));
        btnZoom1.setOnClickListener(v -> cameraManager.setZoomRatio(1.0f));
        btnZoom2.setOnClickListener(v -> cameraManager.setZoomRatio(2.0f));

        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        processImageFromUri(uri);
                    }
                }
        );

        btnGallery.setOnClickListener(v -> galleryLauncher.launch("image/*"));

        permissionHelper = new PermissionHelper(this, new PermissionHelper.PermissionListener() {
            @Override public void onPermissionsGranted() { cameraManager.startCamera(); }
            @Override public void onPermissionsDenied() { finish(); }
        });

        if (permissionHelper.allPermissionsGranted()) cameraManager.startCamera();
        else permissionHelper.requestPermissions();

        btnCapture.setOnClickListener(v -> takePhotoAndProcess());
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    private void setupEnhanceUI() {
        switchEnhance.setOnCheckedChangeListener((buttonView, isChecked) -> {
            enhanceSlidersLayout.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        contrastSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float contrastVal = 1.0f + (progress / 10.0f); // 1.0x do 3.0x
                txtContrast.setText(String.format(Locale.US, "Kontrast: %.1fx", contrastVal));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        brightnessSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int brightnessVal = progress - 50; // -50 do +50
                txtBrightness.setText(String.format(Locale.US, "Jasność: %d", brightnessVal));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    private void processImageFromUri(Uri uri) {
        txtResult.setText("Analizuję zdjęcie z galerii...");

        boolean isEnhanceEnabled = switchEnhance.isChecked();
        float contrastVal = 1.0f + (contrastSlider.getProgress() / 10.0f);
        int brightnessVal = brightnessSlider.getProgress() - 50;

        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            Bitmap loadedBitmap = BitmapFactory.decodeStream(inputStream);
            if (inputStream != null) inputStream.close();

            if (loadedBitmap == null) {
                txtResult.setText("Nie udało się wczytać zdjęcia");
                return;
            }

            int width = loadedBitmap.getWidth();
            int height = loadedBitmap.getHeight();
            int squareSize = Math.min(width, height);
            int startX = (width - squareSize) / 2;
            int startY = (height - squareSize) / 2;

            Bitmap squareBitmap = Bitmap.createBitmap(loadedBitmap, startX, startY, squareSize, squareSize);
            Bitmap croppedAndScaledBitmap = Bitmap.createScaledBitmap(squareBitmap, 1024, 1024, true);

            Bitmap inputBitmapForAI = isEnhanceEnabled
                    ? adjustContrastBrightness(croppedAndScaledBitmap, contrastVal, brightnessVal)
                    : adjustContrastBrightness(croppedAndScaledBitmap, 1.0f, 0);

            cameraExecutor.execute(() -> processAndDrawDetections(inputBitmapForAI, loadedBitmap));
        } catch (Exception e) {
            Log.e("Error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            runOnUiThread(() -> txtResult.setText("Błąd wczytywania zdjęcia z galerii"));
        }
    }

    private void takePhotoAndProcess() {
        txtResult.setText("Analizuję strzał...");

        ImageCapture imageCapture = cameraManager.getImageCapture();
        if (imageCapture == null) {
            txtResult.setText("Czekam na aparat...");
            return;
        }

        boolean isEnhanceEnabled = switchEnhance.isChecked();
        float contrastVal = 1.0f + (contrastSlider.getProgress() / 10.0f);
        int brightnessVal = brightnessSlider.getProgress() - 50;

        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                try {
                    Bitmap rawBitmap = imageProxyToBitmap(image);
                    int rotationDegrees = image.getImageInfo().getRotationDegrees();
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rotationDegrees);
                    Bitmap rotatedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.getWidth(), rawBitmap.getHeight(), matrix, true);

                    View viewFinder = findViewById(R.id.viewFinder);
                    View targetFrame = findViewById(R.id.targetFrame);

                    float viewW = viewFinder.getWidth();
                    float viewH = viewFinder.getHeight();
                    float bmpW = rotatedBitmap.getWidth();
                    float bmpH = rotatedBitmap.getHeight();

                    float scale = Math.max(viewW / bmpW, viewH / bmpH);
                    float dx = (viewW - bmpW * scale) / 2f;
                    float dy = (viewH - bmpH * scale) / 2f;

                    float targetX = targetFrame.getLeft() - viewFinder.getLeft();
                    float targetY = targetFrame.getTop() - viewFinder.getTop();
                    float targetW = targetFrame.getWidth();
                    float targetH = targetFrame.getHeight();

                    int cropX = Math.round((targetX - dx) / scale);
                    int cropY = Math.round((targetY - dy) / scale);
                    int cropW = Math.round(targetW / scale);
                    int cropH = Math.round(targetH / scale);

                    cropX = Math.max(0, Math.min(cropX, rotatedBitmap.getWidth() - 1));
                    cropY = Math.max(0, Math.min(cropY, rotatedBitmap.getHeight() - 1));
                    cropW = Math.max(1, Math.min(cropW, rotatedBitmap.getWidth() - cropX));
                    cropH = Math.max(1, Math.min(cropH, rotatedBitmap.getHeight() - cropY));

                    int squareSize = Math.min(cropW, cropH);
                    Bitmap squareBitmap = Bitmap.createBitmap(rotatedBitmap, cropX, cropY, squareSize, squareSize);
                    Bitmap croppedAndScaledBitmap = Bitmap.createScaledBitmap(squareBitmap, 1024, 1024, true);

                    Bitmap inputBitmapForAI = isEnhanceEnabled
                            ? adjustContrastBrightness(croppedAndScaledBitmap, contrastVal, brightnessVal)
                            : adjustContrastBrightness(croppedAndScaledBitmap, 1.0f, 0);

                    cameraExecutor.execute(() -> processAndDrawDetections(inputBitmapForAI, rawBitmap, rotatedBitmap));
                } catch (Exception e) {
                    Log.e("Error", e.getMessage() != null ? e.getMessage() : "Unknown error");
                    runOnUiThread(() -> txtResult.setText("Błąd analizy"));
                } finally {
                    image.close();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("Error", exception.getMessage());
                runOnUiThread(() -> txtResult.setText("Czekam na strzał..."));
            }
        });
    }

    private void processAndDrawDetections(Bitmap inputBitmapForAI, Bitmap... bitmapsToRecycle) {
        Detection[] wyniki = yoloDetector.processImage(inputBitmapForAI);

        if (wyniki != null && wyniki.length > 0) {
            Bitmap wynikowaBitmapa = inputBitmapForAI.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(wynikowaBitmapa);
            Paint paintKolo = new Paint();
            paintKolo.setColor(Color.RED);
            paintKolo.setStyle(Paint.Style.STROKE);
            paintKolo.setStrokeWidth(6f);
            Paint paintTekst = new Paint();
            paintTekst.setColor(Color.GREEN);
            paintTekst.setTextSize(30f);
            paintTekst.setFakeBoldText(true);

            StringBuilder raport = new StringBuilder();
            int sumaPunktow = 0;
            int detekcjiNaTarczy = 0;

            for (Detection det : wyniki) {
                // Odrzucamy wyłączącnie skrajne narożniki obrazu (logo, napisy w rogu 100x100px)
                boolean isExtremeCorner = (det.x < 100f || det.x > 924f) && (det.y < 100f || det.y > 924f);
                if (isExtremeCorner) {
                    continue;
                }

                detekcjiNaTarczy++;

                // Punktacja bezpośrednio z modelu AI YOLO (det.label = 0..10)
                int punkty = det.label;
                sumaPunktow += punkty;

                canvas.drawCircle(det.x, det.y, 20f, paintKolo);
                canvas.drawText(String.valueOf(punkty), det.x + 22, det.y + 10, paintTekst);
                raport.append("Punkty: ").append(punkty).append("\n");
            }

            if (detekcjiNaTarczy > 0) {
                raport.append("----------------\n");
                raport.append("Suma: ").append(sumaPunktow);

                runOnUiThread(() -> {
                    txtResult.setText(raport.toString());
                    imageViewResult.setImageBitmap(wynikowaBitmapa);
                    imageViewResult.setVisibility(View.VISIBLE);
                    saveResultToGallery(wynikowaBitmapa);
                });
            } else {
                runOnUiThread(() -> {
                    txtResult.setText("Brak przestrzelin na tarczy");
                    Toast.makeText(MainActivity.this, "Brak przestrzelin w strefie tarczy.", Toast.LENGTH_SHORT).show();
                });
            }
        } else {
            runOnUiThread(() -> {
                txtResult.setText("Brak przestrzelin");
                Toast.makeText(MainActivity.this, "AI nie wykryło przestrzelin.", Toast.LENGTH_SHORT).show();
            });
        }

        if (bitmapsToRecycle != null) {
            for (Bitmap b : bitmapsToRecycle) {
                if (b != null && !b.isRecycled()) b.recycle();
            }
        }
    }

    private Bitmap adjustContrastBrightness(Bitmap srcBitmap, float alpha, int beta) {
        Mat mat = new Mat();
        Utils.bitmapToMat(srcBitmap, mat);

        // 1. Wyostrzanie krawędzi (Unsharp Masking) dla małych/rozmytych przestrzelin
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(mat, blurred, new Size(0, 0), 3.0);
        Core.addWeighted(mat, 1.5, blurred, -0.5, 0, mat);
        blurred.release();

        // 2. Adaptacyjna korekcja kontrastu CLAHE w przestrzeni LAB
        Mat labMat = new Mat();
        Imgproc.cvtColor(mat, labMat, Imgproc.COLOR_RGB2Lab);

        List<Mat> labChannels = new ArrayList<>();
        Core.split(labMat, labChannels);

        CLAHE clahe = Imgproc.createCLAHE(alpha * 2.0, new Size(8, 8));
        Mat enhancedL = new Mat();
        clahe.apply(labChannels.get(0), enhancedL);

        if (beta != 0) {
            enhancedL.convertTo(enhancedL, -1, 1.0, beta);
        }

        labChannels.set(0, enhancedL);
        Core.merge(labChannels, labMat);

        Mat dstMat = new Mat();
        Imgproc.cvtColor(labMat, dstMat, Imgproc.COLOR_Lab2RGB);

        Bitmap dstBitmap = Bitmap.createBitmap(srcBitmap.getWidth(), srcBitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(dstMat, dstBitmap);

        mat.release();
        labMat.release();
        enhancedL.release();
        dstMat.release();

        return dstBitmap;
    }

    private void saveResultToGallery(Bitmap bitmap) {
        File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File dir = new File(path, "PrzestrzelinyApp");
        if (!dir.exists()) dir.mkdirs();
        File file = new File(dir, "Wynik_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg");

        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
            MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null, null);
            runOnUiThread(() -> Toast.makeText(this, "Zapisano wynik w Galerii!", Toast.LENGTH_SHORT).show());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionHelper.handlePermissionsResult(requestCode, grantResults);
    }
}
