package com.example.edgeviewer;

import android.Manifest;
import android.graphics.ImageFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.edgeviewer.gl.EdgeGLSurfaceView;
import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    static { System.loadLibrary("edgeviewer"); }

    private native String nativeHello();
    private native int testOpenCV();
    private native void processFrame(byte[] input, int width, int height, byte[] output);
    private native void setViewerMode(int mode);

    private int viewerMode = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EdgeGLSurfaceView glView;
    private byte[] outputBuffer;
    // Temporary debug: when true, send a Java-generated checkerboard to the renderer
    private static final boolean DEBUG_CHECKER = false;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom);
            return insets;
        });

        glView = new EdgeGLSurfaceView(this);
        FrameLayout glContainer = findViewById(R.id.glContainer);
        glContainer.addView(glView);

        ToggleButton toggle = findViewById(R.id.toggleMode);
        toggle.setOnCheckedChangeListener((btn, checked) -> {
            viewerMode = checked ? 0 : 1;
            setViewerMode(viewerMode);
        });
        setViewerMode(viewerMode);

        TextView tv = findViewById(R.id.message);
        handler.post(new Runnable() {
            @Override public void run() {
                if (tv != null && glView != null) {
                    tv.setText(String.format("Mode: %s | FPS: %.1f",
                            (viewerMode==0?"Edges":"Gray"), glView.getFps()));
                }
                handler.postDelayed(this, 500);
            }
        });

        try {
            Log.i("EdgeViewer", "OpenCV test rows=" + testOpenCV());
            Log.i("EdgeViewer", nativeHello());
        } catch (Throwable t) {
            Log.e("EdgeViewer", "JNI test failed", t);
        }

        permissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                PreviewView previewView = findViewById(R.id.previewView);
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

                analysis.setAnalyzer(
                        Executors.newSingleThreadExecutor(),
                        this::analyzeFrame
                );

                CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, selector, preview, analysis);

            } catch (Exception e) {
                Log.e("EdgeViewer", "startCamera failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy proxy) {
        int format = proxy.getFormat();
        if (format != ImageFormat.YUV_420_888 && format != ImageFormat.NV21) {
            proxy.close();
            return;
        }

        int rotation = proxy.getImageInfo().getRotationDegrees();
        int width = proxy.getWidth();
        int height = proxy.getHeight();

        // Prepare NV21 buffer (Y + VU) size
        byte[] nv21 = new byte[width * height + (width * height) / 2];

        // Log plane info for debugging horizontal-line artifacts
        int planeCount = proxy.getPlanes().length;
        android.util.Log.d("EdgeViewer", "analyzeFrame format=" + format + " planes=" + planeCount + " expectedNV21Bytes=" + nv21.length);

        // If the ImageProxy has a single plane, copy the full buffer directly.
        // Do NOT assume ImageFormat.NV21 implies plane layout; rely on planes.length.
        if (planeCount == 1) {
            ByteBuffer buffer = proxy.getPlanes()[0].getBuffer();
            buffer.rewind();
            int toCopy = Math.min(buffer.remaining(), nv21.length);
            buffer.get(nv21, 0, toCopy);
            if (toCopy < nv21.length) {
                // Zero-fill remainder just in case
                for (int i = toCopy; i < nv21.length; i++) nv21[i] = 0;
            }
            android.util.Log.d("EdgeViewer", "single-plane copy size=" + toCopy);
        } else {
            // Robust conversion from YUV_420_888 planes to NV21 (packed VU)
            ImageProxy.PlaneProxy yPlane = proxy.getPlanes()[0];
            ImageProxy.PlaneProxy uPlane = proxy.getPlanes()[1];
            ImageProxy.PlaneProxy vPlane = proxy.getPlanes()[2];

            ByteBuffer yBuf = yPlane.getBuffer();
            ByteBuffer uBuf = uPlane.getBuffer();
            ByteBuffer vBuf = vPlane.getBuffer();

            final int yRowStride = yPlane.getRowStride();
            final int uRowStride = uPlane.getRowStride();
            final int vRowStride = vPlane.getRowStride();
            final int uPixelStride = uPlane.getPixelStride();
            final int vPixelStride = vPlane.getPixelStride();

                // Debug: log strides and plane buffer sizes to help diagnose horizontal line issues
                android.util.Log.d("EdgeViewer", "Y stride=" + yRowStride + " U stride=" + uRowStride + " V stride=" + vRowStride + " U pixStride=" + uPixelStride + " V pixStride=" + vPixelStride
                    + " yBuf=" + yBuf.remaining() + " uBuf=" + uBuf.remaining() + " vBuf=" + vBuf.remaining());

            // Copy Y plane: handle potential row padding
            for (int row = 0; row < height; row++) {
                int yRowStart = row * yRowStride;
                int dstRowStart = row * width;
                for (int col = 0; col < width; col++) {
                    nv21[dstRowStart + col] = yBuf.get(yRowStart + col);
                }
            }

            // Interleave VU (NV21) from U and V planes
            int chromaHeight = height / 2;
            int chromaWidth = width / 2;
            int pos = width * height;
            for (int row = 0; row < chromaHeight; row++) {
                int uRowStart = row * uRowStride;
                int vRowStart = row * vRowStride;
                for (int col = 0; col < chromaWidth; col++) {
                    int uIndex = uRowStart + col * uPixelStride;
                    int vIndex = vRowStart + col * vPixelStride;
                    byte u = uBuf.get(uIndex);
                    byte v = vBuf.get(vIndex);
                    // NV21 expects V then U
                    nv21[pos++] = v;
                    nv21[pos++] = u;
                }
            }
        }

        // Final sanity check: log if we didn't produce expected NV21 length
        if (nv21.length != width * height + (width * height) / 2) {
            android.util.Log.w("EdgeViewer", "nv21 length mismatch: " + nv21.length + " expected=" + (width * height + (width * height) / 2));
        }

        if (outputBuffer == null || outputBuffer.length != width * height)
            outputBuffer = new byte[width * height];

        if (DEBUG_CHECKER) {
            // generate an 8x8 checkerboard for quick visual verification
            int block = Math.max(8, Math.min(width, height) / 16);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int v = (((y / block) + (x / block)) & 1) == 0 ? 255 : 0;
                    outputBuffer[y * width + x] = (byte) v;
                }
            }
        } else {
            processFrame(nv21, width, height, outputBuffer);
        }


        runOnUiThread(() -> {
            glView.setFrameInfo(width, height, rotation);
            glView.updateFrame(outputBuffer, width, height);
        });

        proxy.close();
    }
} 