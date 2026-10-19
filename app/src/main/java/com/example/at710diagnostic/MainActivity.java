package com.example.at710diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.widget.EditText;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.os.Handler;
import android.os.Looper;
import android.os.Handler;
import android.os.Looper;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.io.FileInputStream;

public class MainActivity extends Activity {
    private static final String ACTION_USB_PERMISSION = "com.example.at710diagnostic.USB_PERMISSION";
    private UsbManager usbManager;
    private TextView output;
    private TextView livePh;
    private TextView liveVolume;
    private TextView liveOther;
    private boolean receiverRegistered = false;
    private ParcelFileDescriptor accessoryFd;
    private UsbAccessory activeAccessory;
    private volatile boolean reading = false;
    private final Handler manualHandler = new Handler(Looper.getMainLooper());
    private long manualStartMs = 0L;
    private int manualPromptIndex = -1;
    private TextView manualPromptView;
    private TextView manualElapsedView;
    private EditText manualPhInput;
    private EditText manualCo1Input;
    private EditText manualVolumeInput;
    private EditText manualUnitInput;
    private AlertDialog manualDialog;
    private final int[] manualPromptSeconds = new int[]{0, 90, 180};
    private final String[] manualPromptLabels = new String[]{
        "0:00 — запиши объём (мл), pH и CO1 (мг) с экрана титратора.",
        "1:30 — запиши объём (мл), pH и CO1 (мг). Если CO1 ещё не отображается, оставь поле пустым.",
        "3:00 — последняя из трёх запланированных записей: объём (мл), pH и CO1 (мг)."
    };
    private EditText manualPh, manualCo1, manualVolume, manualNote;
    private TextView guideText;
    private final Handler guideHandler = new Handler(Looper.getMainLooper());
    private boolean guideActive = false;
    private int manualReadingCount = 0;
    private int guideStage = 0;
    private final Runnable guideRunnable = new Runnable() { public void run() { if (!guideActive) return; guideStage++; if (guideText != null) guideText.setText("Подсказка " + guideStage + ": перепиши с экрана pH, C01/CO1 и объём титранта. Затем нажми «Сохранить показание». Следующая подсказка через 30 секунд."); guideHandler.postDelayed(this, 30000); } };

    private final BroadcastReceiver permissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            try {
                if (ACTION_USB_PERMISSION.equals(intent.getAction())) {
                    boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                    UsbAccessory accessory = intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    append("\nРазрешение Android: " + (granted ? "получено" : "отклонено"));
                    if (accessory != null) {
                        if (granted) openAccessoryChannel(accessory);
                        else append("\nДоступ к Android Accessory не разрешён. Нажмите кнопку открытия канала и разрешите доступ.");
                    } else if (device != null && granted) {
                        append("\nРазрешение для USB Host получено. Нажмите «Проверить USB ещё раз».");
                    }
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
        Button open = new Button(this);
        open.setText("Разрешить и открыть канал связи");
        layout.addView(open, new LinearLayout.LayoutParams(-1, -2));
        Button read = new Button(this);
        read.setText("Начать принимать данные от прибора");
        layout.addView(read, new LinearLayout.LayoutParams(-1, -2));
        Button manual = new Button(this);
        manual.setText("Ручной сбор показаний с подсказками");
        layout.addView(manual, new LinearLayout.LayoutParams(-1, -2));
        Button copy = new Button(this);
        copy.setText("Копировать весь текст");
        layout.addView(copy, new LinearLayout.LayoutParams(-1, -2));

        TextView liveTitle = new TextView(this);
        liveTitle.setText("Текущие данные прибора (предварительно)");
        liveTitle.setTextSize(19);
        layout.addView(liveTitle, new LinearLayout.LayoutParams(-1, -2));
        livePh = new TextView(this);
        livePh.setText("Кандидат pH (поле ha): —");
        livePh.setTextSize(24);
        layout.addView(livePh, new LinearLayout.LayoutParams(-1, -2));
        liveVolume = new TextView(this);
        liveVolume.setText("Кандидат объёма (поле j): —");
        liveVolume.setTextSize(20);
        layout.addView(liveVolume, new LinearLayout.LayoutParams(-1, -2));
        liveOther = new TextView(this);
        liveOther.setText("Другие поля: —");
        liveOther.setTextSize(14);
        layout.addView(liveOther, new LinearLayout.LayoutParams(-1, -2));
        TextView liveNote = new TextView(this);
        liveNote.setText("ВНИМАНИЕ: значения ha и j пока не подтверждены как pH и объём. Сверьте их с экраном титратора.");
        liveNote.setTextSize(12);
        layout.addView(liveNote, new LinearLayout.LayoutParams(-1, -2));

        TextView manualTitle = new TextView(this);
        manualTitle.setText("\nРучная запись показаний с экрана титратора");
        manualTitle.setTextSize(19);
        layout.addView(manualTitle, new LinearLayout.LayoutParams(-1, -2));
        TextView manualHelp = new TextView(this);
        manualHelp.setText("Пока протокол не подтверждён, вводи значения прямо с экрана AT-710. Перед запуском титрования запиши исходные значения; во время титрования сохраняй показания по подсказкам. Время запишется автоматически.");
        manualHelp.setTextSize(14);
        layout.addView(manualHelp, new LinearLayout.LayoutParams(-1, -2));
        manualPh = new EditText(this); manualPh.setSingleLine(true); manualPh.setHint("pH с экрана"); manualPh.setInputType(8194); layout.addView(manualPh, new LinearLayout.LayoutParams(-1, -2));
        manualCo1 = new EditText(this); manualCo1.setSingleLine(true); manualCo1.setHint("C01 / CO1 — значение с экрана"); manualCo1.setInputType(8194); layout.addView(manualCo1, new LinearLayout.LayoutParams(-1, -2));
        manualVolume = new EditText(this); manualVolume.setSingleLine(true); manualVolume.setHint("Объём титранта, mL (если есть)"); manualVolume.setInputType(8194); layout.addView(manualVolume, new LinearLayout.LayoutParams(-1, -2));
        manualNote = new EditText(this); manualNote.setSingleLine(true); manualNote.setHint("Примечание: до старта / титрование / конец"); layout.addView(manualNote, new LinearLayout.LayoutParams(-1, -2));
        guideText = new TextView(this); guideText.setText("Подсказки остановлены. Введи показания с экрана."); guideText.setTextSize(14); layout.addView(guideText, new LinearLayout.LayoutParams(-1, -2));
        Button saveManual = new Button(this); saveManual.setText("Сохранить показание с текущим временем"); layout.addView(saveManual, new LinearLayout.LayoutParams(-1, -2));
        Button startGuide = new Button(this); startGuide.setText("Начать подсказки каждые 30 секунд"); layout.addView(startGuide, new LinearLayout.LayoutParams(-1, -2));
        Button stopGuide = new Button(this); stopGuide.setText("Остановить подсказки"); layout.addView(stopGuide, new LinearLayout.LayoutParams(-1, -2));

        output = new TextView(this);
        output.setTextSize(16);
        layout.addView(output, new LinearLayout.LayoutParams(-1, -2));
        ScrollView mainScroll = new ScrollView(this);
        mainScroll.addView(layout, new ScrollView.LayoutParams(-1, -2));
        setContentView(mainScroll);

        try {
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(permissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(permissionReceiver, filter);
            receiverRegistered = true;
        } catch (Exception e) {
            append("Не удалось зарегистрировать обработчик USB-разрешения: " + e.getMessage() + "\n");
        }
        scan.setOnClickListener(v -> scanDevices());
        saveManual.setOnClickListener(v -> saveManualReading());
        startGuide.setOnClickListener(v -> { guideActive = true; guideStage = 0; guideHandler.removeCallbacks(guideRunnable); if (guideText != null) guideText.setText("Начало наблюдения: перепиши pH, C01/CO1 и объём с экрана, затем сохрани. Новая подсказка через 30 секунд."); guideHandler.postDelayed(guideRunnable, 30000); append("\nНачаты ручные подсказки каждые 30 секунд.\n"); });
        stopGuide.setOnClickListener(v -> { guideActive = false; guideHandler.removeCallbacks(guideRunnable); if (guideText != null) guideText.setText("Подсказки остановлены. Записи остаются в журнале на экране."); append("\nПодсказки остановлены.\n"); });
        open.setOnClickListener(v -> requestAccessoryAccess());
        read.setOnClickListener(v -> startReadingAccessory());
        manual.setOnClickListener(v -> startManualObservation());
        copy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("AT-710 диагностика", output.getText().toString()));
                Toast.makeText(this, "Весь текст скопирован", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Не удалось открыть буфер обмена", Toast.LENGTH_SHORT).show();
            }
        });
        scanDevices();
    }


    private void startManualObservation() {
        manualStartMs = android.os.SystemClock.elapsedRealtime();
        manualPromptIndex = -1;

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        panel.setPadding(pad, pad, pad, pad);

        TextView intro = new TextView(this);
        intro.setText("Записывай три строки с экрана KEM AT-710. Единицы в приложении фиксированы: объём — мл, CO1 — мг. Если CO1 ещё не появился, оставь его пустым. Приложение не управляет титратором.");
        intro.setTextSize(15);
        panel.addView(intro);

        manualPromptView = new TextView(this);
        manualPromptView.setTextSize(18);
        manualPromptView.setPadding(0, pad, 0, pad);
        panel.addView(manualPromptView);

        manualElapsedView = new TextView(this);
        manualElapsedView.setText("Время сеанса: 00:00");
        panel.addView(manualElapsedView);

        manualVolumeInput = new EditText(this);
        manualVolumeInput.setHint("Объём раствора, мл");
        manualVolumeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        panel.addView(manualVolumeInput);

        manualPhInput = new EditText(this);
        manualPhInput.setHint("pH");
        manualPhInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        panel.addView(manualPhInput);

        manualCo1Input = new EditText(this);
        manualCo1Input.setHint("CO1, мг");
        manualCo1Input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        panel.addView(manualCo1Input);

        TextView tip = new TextView(this);
        tip.setText("Три записи: сразу (0:00), через 1 минуту 30 секунд и через 3 минуты. Единицы неизменны: объём — мл, CO1 — мг.");
        tip.setTextSize(13);
        tip.setPadding(0, pad, 0, 0);
        panel.addView(tip);

        Button save = new Button(this);
        save.setText("Записать показания в журнал");
        panel.addView(save);

        Button finish = new Button(this);
        finish.setText("Завершить сбор");
        panel.addView(finish);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        manualDialog = new AlertDialog.Builder(this)
            .setTitle("Ручная фиксация KEM AT-710")
            .setView(scroll)
            .setNegativeButton("Закрыть", (d, w) -> manualHandler.removeCallbacksAndMessages(null))
            .create();
        manualDialog.setOnShowListener(d -> {
            manualDialog.getWindow().setLayout(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.92),
                (int) (getResources().getDisplayMetrics().heightPixels * 0.82));
        });
        manualDialog.show();

        save.setOnClickListener(v -> saveManualObservation());
        finish.setOnClickListener(v -> {
            append("\nРучной сбор показаний завершён пользователем.\n");
            manualHandler.removeCallbacksAndMessages(null);
            if (manualDialog != null) manualDialog.dismiss();
        });
        updateManualPrompt();
        manualHandler.post(manualTicker);
    }

    private final Runnable manualTicker = new Runnable() {
        @Override public void run() {
            if (manualDialog == null || !manualDialog.isShowing()) return;
            updateManualPrompt();
            manualHandler.postDelayed(this, 1000);
        }
    };

    private void updateManualPrompt() {
        if (manualStartMs == 0L || manualPromptView == null || manualElapsedView == null) return;
        long elapsed = Math.max(0L, (android.os.SystemClock.elapsedRealtime() - manualStartMs) / 1000L);
        manualElapsedView.setText(String.format(java.util.Locale.US, "Время сеанса: %02d:%02d", elapsed / 60, elapsed % 60));
        int desired = 0;
        for (int i = 0; i < manualPromptSeconds.length; i++) {
            if (elapsed >= manualPromptSeconds[i]) desired = i;
        }
        if (desired != manualPromptIndex) {
            manualPromptIndex = desired;
            manualPromptView.setText("Подсказка (" + formatElapsed(manualPromptSeconds[desired]) + "):\n" + manualPromptLabels[desired]);
            if (desired > 0) {
                Toast.makeText(this, "KEM AT-710: время записать pH и CO1", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String formatElapsed(int seconds) {
        return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void saveManualObservation() {
        String ph = manualPhInput == null ? "" : manualPhInput.getText().toString().trim();
        String co1 = manualCo1Input == null ? "" : manualCo1Input.getText().toString().trim();
        String volume = manualVolumeInput == null ? "" : manualVolumeInput.getText().toString().trim();
        if (ph.isEmpty() && co1.isEmpty() && volume.isEmpty()) {
            Toast.makeText(this, "Введи хотя бы одно значение с экрана прибора", Toast.LENGTH_SHORT).show();
            return;
        }
        long elapsed = Math.max(0L, (android.os.SystemClock.elapsedRealtime() - manualStartMs) / 1000L);
        String record = String.format(java.util.Locale.US,
            "\n[РУЧНОЕ НАБЛЮДЕНИЕ %02d:%02d] Объём=%s мл; pH=%s; CO1=%s мг\n",
            elapsed / 60, elapsed % 60,
            volume.isEmpty() ? "не указано" : volume,
            ph.isEmpty() ? "не указано" : ph,
            co1.isEmpty() ? "нет значения" : co1);
        append(record);
        Toast.makeText(this, "Запись добавлена в журнал.", Toast.LENGTH_SHORT).show();
        manualPhInput.setText("");
        manualCo1Input.setText("");
        manualVolumeInput.setText("");
    }

    private void append(String text) {
        if (output != null) output.append(text);
    }

    private void saveManualReading() {
        String ph = manualPh == null ? "" : manualPh.getText().toString().trim();
        String co1 = manualCo1 == null ? "" : manualCo1.getText().toString().trim();
        String volume = manualVolume == null ? "" : manualVolume.getText().toString().trim();
        String note = manualNote == null ? "" : manualNote.getText().toString().trim();
        if (ph.isEmpty() && co1.isEmpty() && volume.isEmpty()) { Toast.makeText(this, "Введи хотя бы одно значение", Toast.LENGTH_LONG).show(); return; }
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
        manualReadingCount++;
        append("\n[Ручное показание №" + manualReadingCount + "] " + time + "\npH=" + (ph.isEmpty() ? "не записан" : ph) + " | C01/CO1=" + (co1.isEmpty() ? "не записан" : co1) + " | Объём mL=" + (volume.isEmpty() ? "не записан" : volume) + "\nПримечание: " + (note.isEmpty() ? "—" : note) + "\n");
        Toast.makeText(this, "Показание записано в журнал на экране", Toast.LENGTH_SHORT).show();
        if (guideText != null && guideActive) guideText.setText("Показание №" + manualReadingCount + " записано. Следующая подсказка через 30 секунд.");
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
                        if (usbManager.hasPermission(accessory)) {
                            append("Разрешение уже есть. Можно открыть канал кнопкой выше.\n");
                        } else {
                            append("Нужно запросить разрешение Android кнопкой выше.\n");
                        }
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
            append("\nПроверка ADK и приём необработанных байтов доступны. Расшифровка протокола KEM AT-710 пока не реализована.");
        } catch (Exception e) {
            append("\nСканирование прервано безопасно: " + e.getClass().getSimpleName() + ". Отключите USB и повторите.");
        } catch (LinkageError e) {
            append("\nОшибка системного USB-компонента: " + e.getClass().getSimpleName());
        }
    }


    private void startReadingAccessory() {
        if (accessoryFd == null) {
            append("\nСначала нажмите «Разрешить и открыть канал связи».\n");
            return;
        }
        if (reading) {
            append("\nПриём данных уже запущен.\n");
            return;
        }
        reading = true;
        append("\nНачинаю ожидать данные от прибора. Если прибор ничего не передаёт сам, список останется пустым.\n");
        final ParcelFileDescriptor fd = accessoryFd;
        Thread reader = new Thread(() -> {
            FileInputStream input = null;
            long total = 0;
            StringBuilder incomingText = new StringBuilder();
            try {
                input = new FileInputStream(fd.getFileDescriptor());
                byte[] buffer = new byte[256];
                while (reading && accessoryFd == fd) {
                    int count = input.read(buffer);
                    if (count < 0) {
                        appendFromWorker("\nПоток данных завершён прибором.\n");
                        break;
                    }
                    if (count == 0) continue;
                    total += count;
                    StringBuilder hex = new StringBuilder();
                    StringBuilder ascii = new StringBuilder();
                    for (int i = 0; i < count; i++) {
                        int b = buffer[i] & 0xff;
                        if (i > 0) hex.append(' ');
                        hex.append(String.format("%02X", b));
                        ascii.append(b >= 32 && b <= 126 ? (char)b : '.');
                    }
                    appendFromWorker("\nПолучено байт: " + count + " (всего " + total + ")\nHEX: " + hex + "\nТЕКСТ: " + ascii + "\n");
                    for (int i = 0; i < count; i++) {
                        int b = buffer[i] & 0xff;
                        if (b == 0x02 || b == 0x04 || b == 0x0D || b == 0x0A) incomingText.append('\n');
                        else if (b >= 32 && b <= 126) incomingText.append((char)b);
                    }
                    if (incomingText.length() > 20000) incomingText.delete(0, incomingText.length() - 12000);
                    updateLiveValues(incomingText.toString());
                }
            } catch (Exception e) {
                if (reading) appendFromWorker("\nПриём остановлен: " + e.getClass().getSimpleName() + ". Если канал закрыт, откройте его снова.\n");
            } finally {
                reading = false;
            }
        }, "AT710-Accessory-Reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void appendFromWorker(String text) {
        runOnUiThread(() -> append(text));
    }

    private String latestField(String data, String field) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("(?m)^\\s*" + java.util.regex.Pattern.quote(field) + "\\s+([^\\r\\n]+)")
            .matcher(data);
        String found = null;
        while (matcher.find()) {
            String candidate = matcher.group(1).trim();
            java.util.regex.Matcher number = java.util.regex.Pattern.compile("[-+]?[0-9]+(?:\\.[0-9]+)?").matcher(candidate);
            if (number.find()) found = number.group();
        }
        return found;
    }

    private void updateLiveValues(String data) {
        String ha = latestField(data, "ha");
        String hb = latestField(data, "hb");
        String iValue = latestField(data, "i");
        String jValue = latestField(data, "j");
        String kValue = latestField(data, "k");
        String lValue = latestField(data, "l");
        runOnUiThread(() -> {
            if (livePh != null && ha != null) livePh.setText("Кандидат pH (ha): " + ha);
            if (liveVolume != null && jValue != null) liveVolume.setText("Кандидат объёма (j): " + jValue);
            if (liveOther != null) {
                StringBuilder other = new StringBuilder("Другие поля: ");
                if (hb != null) other.append("hb=").append(hb).append("  ");
                if (iValue != null) other.append("i=").append(iValue).append("  ");
                if (kValue != null) other.append("k=").append(kValue).append("  ");
                if (lValue != null) other.append("l=").append(lValue);
                liveOther.setText(other.toString());
            }
        });
    }

    private void requestAccessoryAccess() {
        try {
            UsbAccessory[] accessories = usbManager == null ? null : usbManager.getAccessoryList();
            if (accessories == null || accessories.length == 0) {
                append("\nAndroid Accessory не найден. Проверьте подключение кабеля и нажмите «Проверить USB ещё раз».\n");
                return;
            }
            UsbAccessory accessory = accessories[0];
            activeAccessory = accessory;
            if (usbManager.hasPermission(accessory)) {
                append("\nРазрешение уже получено. Открываю канал…\n");
                openAccessoryChannel(accessory);
            } else {
                Intent intent = new Intent(ACTION_USB_PERMISSION);
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
                PendingIntent pi = PendingIntent.getBroadcast(this, 710, intent, flags);
                usbManager.requestPermission(accessory, pi);
                append("\nНа экране телефона должно появиться окно разрешения. Нажмите «Разрешить».\n");
            }
        } catch (SecurityException e) {
            append("\nAndroid запретил запрос доступа (SecurityException). Закройте приложение, отключите кабель, подключите снова и повторите.\n");
        } catch (Exception e) {
            append("\nНе удалось запросить доступ: " + e.getClass().getSimpleName() + ".\n");
        }
    }

    private void openAccessoryChannel(UsbAccessory accessory) {
        ParcelFileDescriptor newFd = null;
        try {
            if (usbManager == null || accessory == null) {
                append("\nКанал не открыт: аксессуар недоступен.\n");
                return;
            }
            if (accessoryFd != null) {
                try { accessoryFd.close(); } catch (Exception ignored) {}
                accessoryFd = null;
            }
            newFd = usbManager.openAccessory(accessory);
            if (newFd == null) {
                append("\nAndroid не открыл канал (openAccessory вернул null).\n");
                append("Это пока не ошибка протокола: связь ещё не установлена.\n");
                return;
            }
            accessoryFd = newFd;
            activeAccessory = accessory;
            append("\nУСПЕХ: Android открыл канал Android Accessory!\n");
            append("Канал оставлен открытым. Чтение и разбор результатов KEM AT-710 ещё не реализованы.\n");
        } catch (SecurityException e) {
            if (newFd != null) try { newFd.close(); } catch (Exception ignored) {}
            append("\nSecurityException при открытии канала. Приложение не должно вылетать.\n");
            append("Android не выдал приложению доступ к аксессуару; скопируйте этот текст для диагностики.\n");
        } catch (Exception e) {
            if (newFd != null) try { newFd.close(); } catch (Exception ignored) {}
            append("\nНе удалось открыть канал: " + e.getClass().getSimpleName() + ".\n");
        }
    }

    private String safe(String value) { return value == null || value.isEmpty() ? "(не указано)" : value; }

    @Override protected void onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(permissionReceiver); } catch (Exception ignored) {}
            receiverRegistered = false;
        }
        reading = false;
        guideActive = false;
        guideHandler.removeCallbacks(guideRunnable);
        if (accessoryFd != null) {
            try { accessoryFd.close(); } catch (Exception ignored) {}
            accessoryFd = null;
        }
        super.onDestroy();
    }
}
