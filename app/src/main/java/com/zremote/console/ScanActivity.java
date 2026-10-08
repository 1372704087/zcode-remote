package com.zremote.console;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

/**
 * 自定义扫码页：全屏相机预览 + 自绘取景框，风格参考微信/QQ 的扫一扫。
 */
public class ScanActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT = "scan_result";

    private DecoratedBarcodeView barcodeView;
    private ImageView btnTorch;

    private boolean permissionGranted;
    private boolean torchOn;
    private boolean handled;

    private final ActivityResultLauncher<String> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                permissionGranted = granted;
                if (granted) {
                    barcodeView.resume();
                } else {
                    Toast.makeText(this, "需要相机权限才能扫码", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        barcodeView = findViewById(R.id.barcode_view);
        btnTorch = findViewById(R.id.btn_torch);
        findViewById(R.id.btn_back).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        btnTorch.setOnClickListener(v -> toggleTorch());

        // 隐藏 zxing 自带的取景框和文字，改用我们自己的 ScanOverlayView
        barcodeView.getViewFinder().setVisibility(View.GONE);
        View statusView = barcodeView.getStatusView();
        if (statusView != null) {
            statusView.setVisibility(View.GONE);
        }

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true;
            startDecoding();
        } else {
            permLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void startDecoding() {
        barcodeView.decodeSingle(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (handled || result.getText() == null) {
                    return;
                }
                handled = true;
                Intent data = new Intent();
                data.putExtra(EXTRA_RESULT, result.getText());
                setResult(RESULT_OK, data);
                finish();
            }
        });
    }

    private void toggleTorch() {
        torchOn = !torchOn;
        if (torchOn) {
            barcodeView.setTorchOn();
        } else {
            barcodeView.setTorchOff();
        }
        btnTorch.setImageResource(torchOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
        btnTorch.setBackgroundResource(torchOn ? R.drawable.bg_circle_on : R.drawable.bg_circle_dark);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (permissionGranted) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        barcodeView.pause();
        super.onPause();
    }
}