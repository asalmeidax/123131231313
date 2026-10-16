package com.example.uvccapturelite;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.serenegiant.usb.Size;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private AspectRatioSurfaceView cameraView;
    private TextView statusView;
    private TextView profileView;
    private View statusPanel;
    private ICameraHelper cameraHelper;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean surfaceAdded = false;
    private int reconnectAttempts = 0;
    private boolean deviceSelectionRequested = false;

    private final ICameraHelper.StateCallback stateCallback = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(@NonNull UsbDevice device) {
            if (!isVideoDevice(device)) return;
            setStatus("Placa detectada. Abrindo automaticamente…");
            deviceSelectionRequested = true;
            if (cameraHelper != null) cameraHelper.selectDevice(device);
        }

        @Override
        public void onDeviceOpen(@NonNull UsbDevice device, boolean isFirstOpen) {
            setStatus("USB autorizado. Abrindo captura…");
            if (cameraHelper != null) cameraHelper.openCamera();
        }

        @Override
        public void onCameraOpen(@NonNull UsbDevice device) {
            reconnectAttempts = 0;
            startBestPreview();
        }

        @Override
        public void onCameraClose(@NonNull UsbDevice device) {
            removePreviewSurface();
            setStatus("Captura fechada");
        }

        @Override
        public void onDeviceClose(@NonNull UsbDevice device) {
            setStatus("USB fechado");
        }

        @Override
        public void onDetach(@NonNull UsbDevice device) {
            removePreviewSurface();
            deviceSelectionRequested = false;
            setStatus("Placa USB desconectada. Aguardando reconexão…");
            setProfile("Perfil: aguardando dispositivo");
            mainHandler.postDelayed(MainActivity.this::openAttachedCaptureAutomatically, 800);
        }

        @Override
        public void onCancel(@NonNull UsbDevice device) {
            deviceSelectionRequested = false;
            setStatus("Permissão USB não concedida. Reconecte a placa para tentar novamente.");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraView = findViewById(R.id.camera_view);
        statusView = findViewById(R.id.status);
        profileView = findViewById(R.id.profile);
        statusPanel = findViewById(R.id.status_panel);

        enterImmersiveMode();
        initCamera();
        mainHandler.postDelayed(this::openAttachedCaptureAutomatically, 350);

        // Oculta o painel depois de alguns segundos quando o vídeo já estiver rodando.
        mainHandler.postDelayed(() -> {
            if (surfaceAdded) statusPanel.setVisibility(View.GONE);
        }, 6000);
    }

    private void initCamera() {
        if (cameraHelper == null) {
            cameraHelper = new CameraHelper();
            cameraHelper.setStateCallback(stateCallback);
        }
    }

    /**
     * Android TV / projetor: não há botão para iniciar. Ao abrir o app, procuramos
     * imediatamente a primeira interface UVC já conectada e entregamos o dispositivo
     * ao CameraHelper. Se a placa for conectada depois, onAttach() faz a mesma coisa.
     */
    private void openAttachedCaptureAutomatically() {
        if (cameraHelper == null || deviceSelectionRequested) return;

        UsbManager manager = (UsbManager) getSystemService(USB_SERVICE);
        if (manager == null) {
            setStatus("USB Host indisponível neste aparelho");
            return;
        }

        Map<String, UsbDevice> devices = manager.getDeviceList();
        if (devices != null) {
            for (UsbDevice device : devices.values()) {
                if (isVideoDevice(device)) {
                    deviceSelectionRequested = true;
                    setStatus("Placa encontrada. Abrindo automaticamente…");
                    cameraHelper.selectDevice(device);
                    return;
                }
            }
        }

        setStatus("Aguardando placa de captura USB…");
        // Algumas TVs demoram para enumerar o USB após abrir a Activity.
        mainHandler.postDelayed(this::openAttachedCaptureAutomatically, 1200);
    }

    private boolean isVideoDevice(UsbDevice device) {
        if (device == null) return false;
        if (device.getDeviceClass() == UsbConstants.USB_CLASS_VIDEO) return true;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf != null && intf.getInterfaceClass() == UsbConstants.USB_CLASS_VIDEO) {
                return true;
            }
        }
        return false;
    }

    private void startBestPreview() {
        if (cameraHelper == null) return;

        try {
            List<Size> sizes = cameraHelper.getSupportedSizeList();
            Size selected = chooseProfile(sizes, 1280, 720, true);
            if (selected == null) selected = chooseProfile(sizes, 1280, 720, false);
            if (selected == null) selected = chooseProfile(sizes, 640, 480, true);
            if (selected == null && sizes != null && !sizes.isEmpty()) selected = sizes.get(0);

            if (selected != null) {
                try {
                    cameraHelper.setPreviewSize(selected);
                } catch (Throwable ignored) {
                    // Algumas placas rejeitam setPreviewSize mesmo anunciando o modo.
                    // Nesse caso usamos o perfil padrão escolhido pela biblioteca.
                }
            }

            Size active = cameraHelper.getPreviewSize();
            if (active != null) {
                cameraView.setAspectRatio(active.width, active.height);
                setProfile("Perfil: " + active.width + "×" + active.height + " @ " + active.fps + " fps | tipo " + active.type);
            } else if (selected != null) {
                cameraView.setAspectRatio(selected.width, selected.height);
                setProfile("Perfil solicitado: " + selected.width + "×" + selected.height + " @ " + selected.fps + " fps");
            }

            Surface surface = cameraView.getHolder().getSurface();
            if (surface != null && surface.isValid()) {
                cameraHelper.addSurface(surface, false);
                surfaceAdded = true;
            }

            cameraHelper.startPreview();
            setStatus("Captura ativa");

            mainHandler.postDelayed(() -> {
                if (surfaceAdded) statusPanel.setVisibility(View.GONE);
            }, 2500);

        } catch (Throwable error) {
            setStatus("Falha ao iniciar UVC: " + safeMessage(error));
            scheduleReconnect();
        }
    }

    private Size chooseProfile(List<Size> sizes, int width, int height, boolean preferMjpeg) {
        if (sizes == null || sizes.isEmpty()) return null;

        Size best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (Size s : sizes) {
            if (s.width != width || s.height != height) continue;
            // Em UVC, subtype/type 7 corresponde normalmente a MJPEG.
            if (preferMjpeg && s.type != 7) continue;
            int delta = Math.abs(s.fps - 60);
            if (delta < bestDelta) {
                best = s;
                bestDelta = delta;
            }
        }
        return best;
    }

    private void scheduleReconnect() {
        if (cameraHelper == null || reconnectAttempts >= 3) return;
        reconnectAttempts++;
        setStatus("Tentando reconectar (" + reconnectAttempts + "/3)…");
        mainHandler.postDelayed(() -> {
            try {
                removePreviewSurface();
                cameraHelper.closeCamera();
            } catch (Throwable ignored) {}
        }, 1200L * reconnectAttempts);
    }

    private void removePreviewSurface() {
        if (!surfaceAdded || cameraHelper == null || cameraView == null) return;
        try {
            Surface surface = cameraView.getHolder().getSurface();
            if (surface != null) cameraHelper.removeSurface(surface);
        } catch (Throwable ignored) {
        } finally {
            surfaceAdded = false;
        }
    }

    private void setStatus(String text) {
        runOnUiThread(() -> {
            statusPanel.setVisibility(View.VISIBLE);
            statusView.setText(text);
        });
    }

    private void setProfile(String text) {
        runOnUiThread(() -> profileView.setText(text));
    }

    private String safeMessage(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.trim().isEmpty()) ? t.getClass().getSimpleName() : m;
    }

    private void enterImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN &&
                (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            statusPanel.setVisibility(statusPanel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        mainHandler.postDelayed(this::openAttachedCaptureAutomatically, 250);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        removePreviewSurface();
        if (cameraHelper != null) {
            try { cameraHelper.release(); } catch (Throwable ignored) {}
            cameraHelper = null;
        }
        super.onDestroy();
    }
}
