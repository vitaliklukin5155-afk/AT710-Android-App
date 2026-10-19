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
    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                append("\nРазрешение USB: " + (granted ? "получено" : "отклонено"));
                if (device != null && granted) append("\nУстройство доступно. Протокол обмена с прибором пока не реализован.");
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, new IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(permissionReceiver, new IntentFilter(ACTION_USB_PERMISSION));

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
        scan.setOnClickListener(v -> scanDevices());
        scanDevices();
    }

    private void append(String text) { output.append(text); }

    private void scanDevices() {
        output.setText("Проверка USB Host и Android Accessory (ADK)…\n\n");
        if (usbManager == null) { append("USB Manager недоступен на этом устройстве."); return; }

        // Android Accessory mode is different from ordinary USB Host mode.
        UsbAccessory[] accessories = usbManager.getAccessoryList();
        if (accessories == null || accessories.length == 0) {
            append("Режим Android Accessory / ADK: аксессуар не обнаружен.\n");
        } else {
            append("Найдено Android Accessory: " + accessories.length + "\n");
            for (UsbAccessory accessory : accessories) {
                append("Производитель: " + safe(accessory.getManufacturer()) + "\n");
                append("Модель: " + safe(accessory.getModel()) + "\n");
                append("Версия: " + safe(accessory.getVersion()) + "\n");
                append("Описание: " + safe(accessory.getDescription()) + "\n");
                ParcelFileDescriptor fd = usbManager.openAccessory(accessory);
                append(fd != null
                    ? "Android открыл канал accessory. Обмен данными и протокол KEM ещё не реализованы.\n"
                    : "Accessory найден, но открыть канал не удалось.\n");
                if (fd != null) try { fd.close(); } catch (Exception ignored) {}
                append("\n-------------------------\n\n");
            }
        }

        HashMap<String, UsbDevice> devices = usbManager.getDeviceList();
        if (devices.isEmpty()) {
            append("\nОбычный USB Host: устройства не найдены.\n");
        } else {
            append("\nОбычный USB Host: найдено устройств: " + devices.size() + "\n\n");
            for (UsbDevice device : devices.values()) {
                append("Устройство: " + device.getDeviceName() + "\n");
                append(String.format("Vendor ID: 0x%04X (%d)\n", device.getVendorId(), device.getVendorId()));
                append(String.format("Product ID: 0x%04X (%d)\n", device.getProductId(), device.getProductId()));
                append("Класс: " + device.getDeviceClass() + ", подкласс: " + device.getDeviceSubclass() + "\n");
                append("Интерфейсов: " + device.getInterfaceCount() + "\n");
                for (int i = 0; i < device.getInterfaceCount(); i++) {
                    android.hardware.usb.UsbInterface intf = device.getInterface(i);
                    append("  Интерфейс " + i + ": класс=" + intf.getInterfaceClass() + ", подкласс=" + intf.getInterfaceSubclass() + ", endpoints=" + intf.getEndpointCount() + "\n");
                }
                if (!usbManager.hasPermission(device)) {
                    Intent intent = new Intent(ACTION_USB_PERMISSION);
                    PendingIntent pi = PendingIntent.getBroadcast(this, device.getDeviceId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    usbManager.requestPermission(device, pi);
                    append("Запрошено разрешение Android на USB.\n");
                } else {
                    UsbDeviceConnection connection = usbManager.openDevice(device);
                    append(connection != null ? "Android разрешил открыть устройство.\n" : "Разрешение есть, но открыть устройство не удалось.\n");
                    if (connection != null) connection.close();
                }
                append("\n-------------------------\n\n");
            }
        }
        append("\nВажно: эта версия проверяет два режима USB — Host и Accessory/ADK. Она пока не читает результаты титрования и не реализует протокол KEM AT-710.");
    }

    private String safe(String value) { return value == null || value.isEmpty() ? "(не указано)" : value; }

    @Override protected void onDestroy() {
        try { unregisterReceiver(permissionReceiver); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
