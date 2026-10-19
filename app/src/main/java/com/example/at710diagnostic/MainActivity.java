package com.example.at710diagnostic;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.HashMap;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.example.at710diagnostic.USB_PERMISSION";
    private UsbManager usbManager;
    private TextView output;
    private boolean receiverRegistered = false;

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    append("\nРазрешение USB: " + (granted ? "получено" : "отклонено"));
                    if (device != null && granted) append("\nУстройство доступно. Протокол прибора пока не реализован.");
                }
            } catch (Exception e) {
                append("\nОшибка обработки USB-разрешения: " + e.getClass().getSimpleName());
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this);
        title.setText("KEM AT-710 — USB / ADK диагностика");
        title.setTextSize(22);
        layout.addView(title, new LinearLayout.LayoutParams(-1, -2));
        Button scan = new Button(this);
        scan.setText("Проверить USB ещё раз");
        layout.addView(scan, new LinearLayout.LayoutParams(-1, -2));
        output = new TextView(this);
        output.setTextSize(16);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output, new ViewGroup.LayoutParams(-1, -2));
        layout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(layout);

        try {
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(permissionReceiver, filter);
            receiverRegistered = true;
        } catch (Exception e) {
            append("Не удалось зарегистрировать обработчик USB-разрешения: " + e.getMessage() + "\n");
        }
        scan.setOnClickListener(v -> scanDevices());
        scanDevices();
    }

    private void append(String text) {
        if (output != null) output.append(text);
    }

    private void scanDevices() {
        try {
            output.setText("Проверка USB Host и Android Accessory (ADK)…\n\n");
            if (usbManager == null) {
                append("USB Manager недоступен на этом устройстве.");
                return;
            }
            UsbAccessory[] accessories = usbManager.getAccessoryList();
            if (accessories == null || accessories.length == 0) {
                append("Режим Android Accessory / ADK: аксессуар не обнаружен.\n");
            } else {
                append("Найдено Android Accessory: " + accessories.length + "\n");
                for (UsbAccessory accessory : accessories) {
                    ParcelFileDescriptor fd = null;
                    try {
                        append("Производитель: " + safe(accessory.getManufacturer()) + "\n");
                        append("Модель: " + safe(accessory.getModel()) + "\n");
                        append("Версия: " + safe(accessory.getVersion()) + "\n");
                        append("Accessory обнаружен; канал не открываем без отдельного разрешения Android.\\n");
                    } catch (Exception e) {
                        append("Ошибка accessory: " + e.getClass().getSimpleName() + "\n");
                    } finally {
                        if (fd != null) try { fd.close(); } catch (Exception ignored) {}
                    }
                }
            }

            HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
            if (devices.isEmpty()) {
                append("\nОбычный USB Host: устройства не найдены.\n");
            } else {
                append("\nОбычный USB Host: найдено устройств: " + devices.size() + "\n\n");
                for (UsbDevice device : devices.values()) {
                    UsbDeviceConnection connection = null;
                    try {
                        append("Устройство: " + device.getDeviceName() + "\n");
                        append(String.format("Vendor ID: 0x%04X (%d)\n", device.getVendorId(), device.getVendorId()));
                        append(String.format("Product ID: 0x%04X (%d)\n", device.getProductId(), device.getProductId()));
                        append("Класс: " + device.getDeviceClass() + ", интерфейсов: " + device.getInterfaceCount() + "\n");
                        if (!usbManager.hasPermission(device)) {
                            Intent intent = new Intent(ACTION_USB_PERMISSION);
                            PendingIntent pi = PendingIntent.getBroadcast(this, device.getDeviceId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                            usbManager.requestPermission(device, pi);
                            append("Запрошено разрешение Android на USB.\n");
                        } else {
                            connection = usbManager.openDevice(device);
                            append(connection != null ? "Android открыл USB-устройство.\n" : "Android не смог открыть устройство.\n");
                        }
                    } catch (Exception e) {
                        append("Ошибка устройства: " + e.getClass().getSimpleName() + "\n");
                    } finally {
                        if (connection != null) try { connection.close(); } catch (Exception ignored) {}
                    }
                    append("\n-------------------------\n\n");
                }
            }
            append("\nЭта версия только диагностирует USB Host и Accessory/ADK. Протокол KEM AT-710 и чтение результатов пока не реализованы.");
        } catch (Exception e) {
            append("\nСканирование прервано безопасно: " + e.getClass().getSimpleName() + ". Отключите USB и повторите.");
        } catch (LinkageError e) {
            append("\nОшибка системного USB-компонента: " + e.getClass().getSimpleName());
        }
    }

    private String safe(String value) { return value == null || value.isEmpty() ? "(не указано)" : value; }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(permissionReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        super.onDestroy();
    }
}
