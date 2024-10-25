package com.example.facedetection;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.facedetection.FloatingOverlayService;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.face.FaceLandmark;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView textOverlay;
    private static final float SENSITIVITY = 70f;  // Adjust this sensitivity for eye movement responsiveness
    private float previousX = -1, previousY = -1;  // Store previous eye center positions to track movement
    private static final int REQUEST_OVERLAY_PERMISSION = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        previewView = findViewById(R.id.cameraPreview);
        textOverlay = findViewById(R.id.textOverlay);

        // Check for overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        } else {
            startFloatingOverlayService();
        }

        startCamera();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(this)) {
                startFloatingOverlayService();
            } else {
                Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startFloatingOverlayService() {
        Intent serviceIntent = new Intent(this, FloatingOverlayService.class);
        startService(serviceIntent);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("Camera", "Error starting camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        FaceDetectorOptions faceDetectorOptions = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();

        FaceDetector faceDetector = FaceDetection.getClient(faceDetectorOptions);

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            processImageProxy(faceDetector, imageProxy);
        });

        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(FaceDetector faceDetector, ImageProxy imageProxy) {
        if (imageProxy.getImage() != null) {
            InputImage image = InputImage.fromMediaImage(imageProxy.getImage(), imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(image)
                    .addOnSuccessListener(this::processFaces)
                    .addOnFailureListener(e -> Log.e("FaceDetection", "Error: " + e.getMessage()))
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private void processFaces(List<Face> faces) {
        StringBuilder resultText = new StringBuilder();

        for (Face face : faces) {
            FaceLandmark leftEye = face.getLandmark(FaceLandmark.LEFT_EYE);
            FaceLandmark rightEye = face.getLandmark(FaceLandmark.RIGHT_EYE);

            boolean isRightEyeBlinking = face.getRightEyeOpenProbability() != null && face.getRightEyeOpenProbability() < 0.2;

            if (leftEye != null && rightEye != null) {
                float eyeCenterX = (leftEye.getPosition().x + rightEye.getPosition().x) / 2;
                float eyeCenterY = (leftEye.getPosition().y + rightEye.getPosition().y) / 2;

                resultText.append("Left Eye: (")
                        .append(leftEye.getPosition().x).append(", ")
                        .append(leftEye.getPosition().y).append(")\n");

                resultText.append("Right Eye: (")
                        .append(rightEye.getPosition().x).append(", ")
                        .append(rightEye.getPosition().y).append(")\n");

                if (previousX != -1 && previousY != -1) {
                    float deltaX = eyeCenterX - previousX;
                    float deltaY = eyeCenterY - previousY;

                    // Broadcast eye movement data to the floating service
                    sendMovementData(-deltaX * SENSITIVITY, deltaY * SENSITIVITY, isRightEyeBlinking);
                }

                previousX = eyeCenterX;
                previousY = eyeCenterY;
            } else {
                resultText.append("Eyes Not Detected Properly\n");
            }
        }

        if (faces.isEmpty()) {
            resultText.append("No faces detected\n");
        }

        textOverlay.setText(resultText.toString());
    }

    private void sendMovementData(float deltaX, float deltaY, boolean isRightEyeBlinking) {
        Intent intent = new Intent("UPDATE_CIRCULAR_OBJECT");

        // Set the color based on eye blinking
        int color = isRightEyeBlinking ? android.graphics.Color.RED : android.graphics.Color.CYAN;
        intent.putExtra("deltaX", deltaX);
        intent.putExtra("deltaY", deltaY);
        intent.putExtra("color", color);

        sendBroadcast(intent);
    }
}
