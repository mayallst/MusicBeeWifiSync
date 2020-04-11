package com.getmusicbee.musicbeewifisync;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.FileHandler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

class Dialog {
    static int showOkCancel(final Activity parentActivity, final String prompt) {
        final Object dialogWait = new Object();
        final AtomicInteger result = new AtomicInteger();
        parentActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder errorDialog = new AlertDialog.Builder(parentActivity);
                errorDialog.setMessage(prompt);
                errorDialog.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.set(android.R.string.cancel);
                        synchronized (dialogWait) {
                            dialogWait.notifyAll();
                        }
                    }
                });
                errorDialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        result.set(android.R.string.ok);
                        synchronized (dialogWait) {
                            dialogWait.notifyAll();
                        }
                    }
                });
                errorDialog.setCancelable(false);
                errorDialog.show();
            }
        });
        synchronized (dialogWait) {
            try {
                dialogWait.wait();
            } catch (Exception  ex){
            }
        }
        return result.get();
    }
}

class ErrorHandler {
    private static File folderPath;
    private static FileHandler fileHandler = null;
    private final static Logger logger = Logger.getLogger("WifiSync");

    static void initialise(Context context) {
        folderPath = context.getFilesDir();
    }

    private static void initialise() {
        if (fileHandler == null) {
            try {
                File file = new File(folderPath, "MusicBeeWifiSyncErrorLog.txt");
                fileHandler = new FileHandler(file.getPath());
                fileHandler.setFormatter(new LogFormatter());
                logger.addHandler(fileHandler);
                logger.info(Build.MODEL + ";  " + Build.VERSION.RELEASE + ";  " + BuildConfig.VERSION_NAME);
            } catch (Exception  ex){
                Log.e("WifiSync: ErrorHandler", ex.toString());
            }
        }
    }

    static String getLog() {
        try {
            final File errorLog = new File(folderPath, "MusicBeeWifiSyncErrorLog.txt");
            try (final InputStream stream = new FileInputStream(errorLog)) {
                byte[] buffer = new byte[(int) errorLog.length()];
                stream.read(buffer, 0, buffer.length);
                return new String(buffer, StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            return null;
        }
    }

    static void logError(String tag, String message){
        if (message != null) {
            initialise();
            logger.severe(tag + ": " + message);
        }
    }

    static void logError(String tag, Exception  ex){
        logError(tag, ex, null);
    }

    static void logError(String tag, Exception  ex, String info){
        initialise();
        String message = ex.toString();
        logger.severe(tag + ": " + message + ((info == null) ? "" : ": " + info));
        if (WifiSyncServiceSettings.debugMode) {
            for (StackTraceElement element : ex.getStackTrace()) {
                logger.info(element.toString());
            }
        } else if (BuildConfig.DEBUG) {
            Log.d("WifiSync: " + tag, message);
            for (StackTraceElement element : ex.getStackTrace()) {
                Log.d("WifiSync", element.toString());
            }
        }
    }

    static void logInfo(String tag, String message){
        if (message != null) {
            initialise();
            logger.info((tag == null) ? message : tag + ": " + message);
        }
    }

    private static class LogFormatter extends SimpleFormatter {
        private boolean writeHeader = true;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            StringBuilder builder = new StringBuilder();
            if (writeHeader) {
                writeHeader = false;
                builder.append(dateFormat.format(new Date(record.getMillis())));
            } else {
                builder.append(timeFormat.format(new Date(record.getMillis())));
            }
            builder.append(": ");
            builder.append(record.getMessage());
            builder.append("\n");
            return builder.toString();
        }
    }
}

class CaseInsensitiveMap extends HashMap<String, String> {
    @Override
    public String put(String key, String value) {
        return super.put(key.toLowerCase(Locale.ROOT), value);
    }

    String get(String key) {
        return super.get(key.toLowerCase(Locale.ROOT));
    }

    boolean containsKey(String key) {
        return super.containsKey(key.toLowerCase(Locale.ROOT));
    }
}

