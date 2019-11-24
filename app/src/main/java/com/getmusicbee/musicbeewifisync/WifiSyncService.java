package com.getmusicbee.musicbeewifisync;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.FileObserver;
import android.os.IBinder;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidObjectException;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;
import android.support.v4.provider.DocumentFile;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

public class WifiSyncService extends Service {
    static final AtomicBoolean syncIsRunning = new AtomicBoolean();
    static final AtomicInteger syncPercentCompleted = new AtomicInteger();
    static final AtomicInteger syncErrorMessageId = new AtomicInteger();
    static final AtomicReference<String> syncProgressMessage = new AtomicReference<>("");
    static final ArrayList<FileErrorInfo> syncFailedFiles = new ArrayList<>();
    static final AutoResetEvent resultsActivityReady = new AutoResetEvent(false);
    static int syncIteration;
    static volatile ArrayList<SyncResultsInfo> syncToResults;
    static volatile ArrayList<SyncResultsInfo> syncFromResults;
    static final AutoResetEvent waitSyncResults = new AutoResetEvent(false);
    private final AtomicInteger syncFileScanCount = new AtomicInteger(0);
    private boolean settingsSyncFromMusicBee;
    private boolean settingsSyncPreview;
    private boolean settingsSyncIgnoreErrors;
    private String settingsDefaultIpAddressValue;
    private String settingsDeviceName;
    private int settingsDeviceStorageIndex;
    private Uri settingsAccessPermissionsUri;
    private boolean settingsSyncCustomFiles;
    private boolean settingsSyncDeleteUnselectedFiles;
    private ArrayList<String> settingsSyncCustomPlaylistNames;
    private int settingsReverseSyncPlayer;
    private boolean settingsReverseSyncPlaylists;
    private String settingsReverseSyncPlaylistsPath;
    private boolean settingsReverseSyncRatings;
    private boolean settingsReverseSyncPlayCounts;
    private Thread syncWorkerThread = null;
    private FileStorageAccess storage;
    private static final int socketConnectTimeout = 2000;
    private static final int socketReadTimeout = 30000;
    private static final int socketReadBufferLength = 131072;
    private static final int FOREGROUND_ID = 2938;
    private static final int serverPort = 27304;
    private static final String intentNameDefaultIpAddressValue = "defaultIpAddressValue";
    private static final String intentNameDeviceName = "deviceName";
    private static final String intentNameDeviceStorageIndex = "deviceStorageIndex";
    private static final String intentNameAccessPermissionsUri = "accessPermissionsUri";
    private static final String intentNameSyncIteration = "syncIteration";
    private static final String intentNameSyncPreview = "syncPreview";
    private static final String intentNameSyncFromMusicBee = "syncFromMusicBee";
    private static final String intentNameSyncCustomFiles = "syncCustomFiles";
    private static final String intentNameSyncDeleteUnselectedFiles = "syncDeleteUnselectedFiles";
    private static final String intentNameSyncCustomPlaylistNames = "syncCustomPlaylistNames";
    private static final String intentNameSyncIgnoreErrors = "syncIgnoreErrors";
    private static final String intentNameReverseSyncPlayer = "reverseSyncPlayer";
    private static final String intentNameReverseSyncPlaylists = "reverseSyncPlaylists";
    private static final String intentNameReverseSyncPlaylistsPath = "reverseSyncPlaylistsPath";
    private static final String intentNameReverseSyncRatings = "reverseSyncRatings";
    private static final String intentNameReverseSyncPlayCounts = "reverseSyncPlayCounts";
    private static final String commandSyncDevice = "SyncDevice";
    private static final String commandSyncToDevice = "SyncToDevice";
    private static final String syncStatusOK = "OK";
    private static final String syncStatusFAIL = "FAIL";
    private static final String syncStatusCANCEL = "CANCEL";
    private static final String syncEndOfData = "";
    private static final String serverHelloPrefix = "MusicBeeWifiSyncServer/";
    private static final String clientHelloVersion = "MusicBeeWifiSyncClient/1.0";

    static void startSynchronisation(Context context, int iteration, boolean syncPreview, boolean syncIgnoreErrors) {
        if (WifiSyncServiceSettings.debugMode) {
            ErrorHandler.logInfo("startSync", "preview=" + syncPreview + ",iteration=" + iteration);
        }
        Intent intent;
        if (syncPreview) {
            waitSyncResults.reset();
            intent = new Intent(context, SyncResultsPreviewActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } else {
            intent = new Intent(context, SyncResultsStatusActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        }
        intent = new Intent();
        intent.setClass(context, WifiSyncService.class);
        intent.setAction(context.getString(R.string.actionSyncStart));
        intent.putExtra(intentNameDefaultIpAddressValue, WifiSyncServiceSettings.defaultIpAddressValue);
        intent.putExtra(intentNameDeviceName, WifiSyncServiceSettings.deviceName);
        intent.putExtra(intentNameDeviceStorageIndex, WifiSyncServiceSettings.deviceStorageIndex);
        intent.putExtra(intentNameAccessPermissionsUri, WifiSyncServiceSettings.accessPermissionsUri.get());
        intent.putExtra(intentNameSyncIteration, iteration);
        intent.putExtra(intentNameSyncPreview, syncPreview);
        intent.putExtra(intentNameSyncFromMusicBee, WifiSyncServiceSettings.syncFromMusicBee);
        intent.putExtra(intentNameSyncCustomFiles, WifiSyncServiceSettings.syncCustomFiles);
        intent.putStringArrayListExtra(intentNameSyncCustomPlaylistNames, WifiSyncServiceSettings.syncCustomPlaylistNames);
        intent.putExtra(intentNameSyncDeleteUnselectedFiles, WifiSyncServiceSettings.syncDeleteUnselectedFiles);
        intent.putExtra(intentNameSyncIgnoreErrors, syncIgnoreErrors);
        intent.putExtra(intentNameReverseSyncPlayer, WifiSyncServiceSettings.reverseSyncPlayer);
        intent.putExtra(intentNameReverseSyncPlaylists, WifiSyncServiceSettings.reverseSyncPlaylists);
        intent.putExtra(intentNameReverseSyncPlaylistsPath, WifiSyncServiceSettings.reverseSyncPlaylistsPath);
        intent.putExtra(intentNameReverseSyncRatings, WifiSyncServiceSettings.reverseSyncRatings);
        intent.putExtra(intentNameReverseSyncPlayCounts, WifiSyncServiceSettings.reverseSyncPlayCounts);
        context.startService(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("MusicBeeChannel_01", getString(R.string.title_channel_name), NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "MusicBeeChannel_01")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.syncNotificationInfo))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        Intent stopIntent = new Intent(this, WifiSyncService.class);
        stopIntent.setAction(getString(R.string.actionSyncAbort));
        PendingIntent pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, 0);
        NotificationCompat.Action stopAction = new NotificationCompat.Action(android.R.drawable.ic_delete, getString(R.string.syncStop), pendingStopIntent);
        builder.addAction(stopAction);
        startForeground(FOREGROUND_ID, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action != null) {
            if (WifiSyncServiceSettings.debugMode) {
                ErrorHandler.logInfo("command", "action=" + action);
            }
            if (action.equals(getString(R.string.actionSyncStart))) {
                syncIsRunning.set(true);
                syncPercentCompleted.set(0);
                syncProgressMessage.set("");
                syncErrorMessageId.set(0);
                syncFileScanCount.set(0);
                syncFailedFiles.clear();
                syncIteration = intent.getIntExtra(intentNameSyncIteration, 0);
                settingsDefaultIpAddressValue = intent.getStringExtra(intentNameDefaultIpAddressValue);
                settingsDeviceName = intent.getStringExtra(intentNameDeviceName);
                settingsDeviceStorageIndex = intent.getIntExtra(intentNameDeviceStorageIndex, 0);
                settingsAccessPermissionsUri = intent.getParcelableExtra(intentNameAccessPermissionsUri);
                settingsSyncPreview = intent.getBooleanExtra(intentNameSyncPreview, false);
                settingsSyncFromMusicBee = intent.getBooleanExtra(intentNameSyncFromMusicBee, true);
                settingsSyncIgnoreErrors = intent.getBooleanExtra(intentNameSyncIgnoreErrors, false);
                settingsReverseSyncPlayer = intent.getIntExtra(intentNameReverseSyncPlayer, 0);
                settingsReverseSyncPlaylists = intent.getBooleanExtra(intentNameReverseSyncPlaylists, false);
                settingsReverseSyncPlaylistsPath = intent.getStringExtra(intentNameReverseSyncPlaylistsPath);
                settingsReverseSyncRatings = intent.getBooleanExtra(intentNameReverseSyncRatings, false);
                settingsReverseSyncPlayCounts = intent.getBooleanExtra(intentNameReverseSyncPlayCounts, false);
                settingsSyncDeleteUnselectedFiles = intent.getBooleanExtra(intentNameSyncDeleteUnselectedFiles, false);
                settingsSyncCustomFiles = intent.getBooleanExtra(intentNameSyncCustomFiles, false);
                settingsSyncCustomPlaylistNames = intent.getStringArrayListExtra(intentNameSyncCustomPlaylistNames);
                syncWorkerThread = new Thread(new SynchronisationWorker());
                syncWorkerThread.start();
            } else if (action.equals(getString(R.string.actionSyncAbort))) {
                syncIsRunning.set(false);
                syncPercentCompleted.set(-1);
                if (syncWorkerThread != null) {
                    syncWorkerThread.interrupt();
                }
            }
        }
        return START_REDELIVER_INTENT;
    }

    private class SynchronisationWorker implements Runnable {
        private Socket clientSocket;
        private InputStream socketInputStream;
        private DataInputStream socketStreamReader;
        private OutputStream socketOutputStream;
        private DataOutputStream socketStreamWriter;

        @Override
        public void run() {
            try {
                boolean anyConnections = tryStartSynchronisation(InetAddress.getByName(settingsDefaultIpAddressValue));
                if (!anyConnections) {
                    if (WifiSyncServiceSettings.debugMode) {
                        ErrorHandler.logInfo("worker", "no connection for "+ settingsDefaultIpAddressValue + " - trying again");
                    }
                    ArrayList<CandidateIpAddress> candidateAddresses = findCandidateIpAddresses();
                    for (CandidateIpAddress candidate : candidateAddresses) {
                        if (tryStartSynchronisation(candidate.address)) {
                            anyConnections = true;
                            WifiSyncServiceSettings.defaultIpAddressValue = candidate.toString();
                            break;
                        }
                    }
                }
                if (!anyConnections) {
                    syncErrorMessageId.set(R.string.errorServerNotFound);
                }
            } catch (InterruptedException ex) {
                if (WifiSyncServiceSettings.debugMode) {
                    ErrorHandler.logError("worker", ex);
                }
            } catch (Exception ex) {
                ErrorHandler.logError("worker", ex);
                syncErrorMessageId.set(R.string.errorServerNotFound);
            }
            syncPercentCompleted.set(-1);
            syncIsRunning.set(false);
            stopForeground(true);
            stopSelf();
        }

        private boolean tryStartSynchronisation(InetAddress address) throws InterruptedException {
            boolean serverLocated = false;
            try {
                int socketFailRetryAttempts = 0;
                while (true) {
                    if (socketFailRetryAttempts > 0) {
                        // allow some time for the server to re-open the listener
                        Thread.sleep(1000);
                    }
                    if (WifiSyncServiceSettings.debugMode) {
                        ErrorHandler.logInfo("tryStart", "connecting " + address.toString() + ", attempt=" + socketFailRetryAttempts);
                    }
                    try (Socket clientSocket = new Socket()) {
                        this.clientSocket = clientSocket;
                        clientSocket.connect(new InetSocketAddress(address, serverPort), socketConnectTimeout);
                        if (WifiSyncServiceSettings.debugMode) {
                            ErrorHandler.logInfo("tryStart", "connected");
                        }
                        clientSocket.setReceiveBufferSize(262144);
                        clientSocket.setSendBufferSize(65536);
                        clientSocket.setPerformancePreferences(0, 0, 1);
                        clientSocket.setTcpNoDelay(true);
                        try (InputStream socketInputStream = clientSocket.getInputStream()) {
                        try (BufferedInputStream bufferedSocketInputStream = new BufferedInputStream(socketInputStream, 8192)) {
                        try (DataInputStream socketStreamReader = new DataInputStream((bufferedSocketInputStream))) {
                        try (OutputStream socketOutputStream = clientSocket.getOutputStream()) {
                        try (BufferedOutputStream bufferedSocketOutputStream = new BufferedOutputStream(socketOutputStream, 65536)) {
                        try (DataOutputStream socketStreamWriter = new DataOutputStream(bufferedSocketOutputStream)) {
                            this.socketInputStream = socketInputStream;
                            this.socketStreamReader = socketStreamReader;
                            this.socketOutputStream = socketOutputStream;
                            this.socketStreamWriter = socketStreamWriter;
                            int failedSyncFilesCount = 0;
                            try {
                                clientSocket.setSoTimeout(socketReadTimeout);
                                String hello = readString();
                                serverLocated = hello.startsWith(serverHelloPrefix);
                                if (WifiSyncServiceSettings.debugMode) {
                                    ErrorHandler.logInfo("tryStart", "hello=" + serverLocated + ",fromMB=" + settingsSyncFromMusicBee + ",custfiles=" + settingsSyncCustomFiles + ",preview=" + settingsSyncPreview + ",dev=" + settingsDeviceName + "," + settingsDeviceStorageIndex);
                                }
                                if (!serverLocated) {
                                    return false;
                                }
                                writeString(clientHelloVersion);
                                writeString((settingsSyncCustomFiles) ? commandSyncToDevice : commandSyncDevice);
                                writeByte((!settingsSyncPreview) ? 0 : 1);
                                writeString(settingsDeviceName);
                                writeByte(settingsDeviceStorageIndex);
                                if (!settingsSyncCustomFiles) {
                                    writeString((settingsSyncFromMusicBee) ? "F" : "T");
                                    writeString("F");
                                    writeString("0");
                                } else {
                                    writeString("T");
                                    writeString((!settingsSyncDeleteUnselectedFiles) ? "F" : "T");
                                    writeString(String.valueOf(settingsSyncCustomPlaylistNames.size()));
                                    for (String playlistName : settingsSyncCustomPlaylistNames) {
                                        writeString(playlistName);
                                    }
                                }
                                writeString(syncEndOfData);
                                flushWriter();
                                boolean storageProfileMatched = (readByte() != 0);
                                readToEndOfCommand();
                                if (!storageProfileMatched) {
                                    syncErrorMessageId.set(R.string.errorConfigNotMatched);
                                    setPreviewFailed();
                                    return true;
                                } else {
                                    File sdCard = FileStorageAccess.getSdCardFromIndex(getApplicationContext(), settingsDeviceStorageIndex);
                                    if (sdCard == null) {
                                        if (WifiSyncServiceSettings.debugMode) {
                                            ErrorHandler.logInfo("tryStart", "SD Card not found");
                                        }
                                        writeString(syncStatusFAIL);
                                        flushWriter();
                                        syncErrorMessageId.set(R.string.errorSdCardNotFound);
                                        return true;
                                    }
                                    storage = new FileStorageAccess(getApplicationContext(), sdCard.getPath(), settingsAccessPermissionsUri);
                                }
                                writeString("MOUNTED");
                                failedSyncFilesCount = syncFailedFiles.size();
                                syncDevice();
                            } catch (InterruptedException ex) {
                                if (WifiSyncServiceSettings.debugMode) {
                                    ErrorHandler.logError("tryStart", ex);
                                }
                                if (storage != null) {
                                    storage.waitScanFiles();
                                }
                                throw ex;
                            } catch (SocketException ex) {
                                ErrorHandler.logError("tryStart" + socketFailRetryAttempts, ex);
                                if (storage != null) {
                                    storage.waitScanFiles();
                                }
                                if (socketFailRetryAttempts > 16) {
                                    syncErrorMessageId.set(R.string.errorSyncNonSpecific);
                                } else {
                                    socketFailRetryAttempts ++;
                                    if (syncFailedFiles.size() > failedSyncFilesCount) {
                                        syncFailedFiles.remove(failedSyncFilesCount);
                                    }
                                    continue;
                                }
                            } catch (Exception ex) {
                                ErrorHandler.logError("tryStart" + socketFailRetryAttempts, ex);
                                if (storage != null) {
                                    storage.waitScanFiles();
                                }
                                syncErrorMessageId.set(R.string.errorSyncNonSpecific);
                                setPreviewFailed();
                            }
                            return true;
                        }}}}}}
                    }
                }
            } catch (InterruptedException ex) {
                if (WifiSyncServiceSettings.debugMode) {
                    ErrorHandler.logError("tryStart", ex);
                }
                throw ex;
            } catch (SocketTimeoutException ex) {
                if (WifiSyncServiceSettings.debugMode) {
                    ErrorHandler.logError("tryStart", ex);
                }
                syncErrorMessageId.set(R.string.errorServerNotFound);
                setPreviewFailed();
            } catch (Exception ex) {
                ErrorHandler.logError("tryStart", ex);
                syncErrorMessageId.set(R.string.errorSyncNonSpecific);
                setPreviewFailed();
            }
            return serverLocated;
        }

        private void setPreviewFailed() {
            syncFromResults = null;
            waitSyncResults.set();
        }

        private void syncDevice() throws Exception {
            if (WifiSyncServiceSettings.debugMode) {
                ErrorHandler.logInfo("syncDevice", "root=" + storage.storageRootPath + ",ignoreErrors=" + settingsSyncIgnoreErrors + ",playlists=" + settingsReverseSyncPlaylists + ",ratings=" + settingsReverseSyncRatings + ",playcount=" + settingsReverseSyncPlayCounts);
            }
            syncFromResults = null;
            if (!settingsSyncCustomFiles) {
                syncToResults = null;
                writeByte(syncIteration);
                writeByte((!settingsSyncIgnoreErrors) ? 0 : 1);
                writeByte((!settingsReverseSyncPlaylists) ? 0 : 1);
                writeByte((!settingsReverseSyncRatings) ? 0 : 1);
                writeByte((!settingsReverseSyncPlayCounts) ? 0 : 1);
                writeString(storage.storageRootPath);
                writeString(syncEndOfData);
            } else {
                syncToResults = new ArrayList<>();
            }
            flushWriter();
            String command;
            commandLoop:
            while (true) {
                command = readString();
                if (WifiSyncServiceSettings.debugMode) {
                    ErrorHandler.logInfo("syncDevice", "command=" + command);
                }
                if (Thread.interrupted()) {
                    if (WifiSyncServiceSettings.debugMode) {
                        ErrorHandler.logInfo("syncDevice", "interrupted");
                    }
                    throw new InterruptedException();
                }
                switch (command) {
                    case "GetCapability":
                        getCapability();
                        break;
                    case "GetFiles":
                        getFiles();
                        break;
                    case "ShowDeleteConfirmation":
                        showDeleteConfirmation();
                        break;
                    case "ReceiveFile":
                        receiveFile();
                        break;
                    case "DeleteFiles":
                        deleteFiles();
                        break;
                    case "DeleteFolders":
                        deleteFolders();
                        break;
                    case "SendFile":
                        sendFile();
                        break;
                    case "SendPlaylists":
                        sendPlaylists();
                        break;
                    case "SendStats":
                        sendStats();
                        break;
                    case "ShowResults":
                        getReverseSyncPreviewResults();
                        break;
                    case "ShowPreviewResults":
                        showSyncPreview();
                        break commandLoop;
                    case "Exit":
                        exitSynchronisation();
                        break commandLoop;
                    default:
                        break commandLoop;
                }
            }
            if (WifiSyncServiceSettings.debugMode) {
                ErrorHandler.logInfo("syncDevice", "exit");
            }
        }

        private void readToEndOfCommand() throws SocketException {
            try {
                while (socketStreamReader.read() != 27) {
                }
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private int getAvailableData() throws SocketException {
            try {
                return socketInputStream.available();
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private void flushWriter() throws SocketException {
            try {
                socketStreamWriter.flush();
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private String readString() throws SocketException {
            try {
                return socketStreamReader.readUTF();
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private void writeString(String value) throws SocketException {
            try {
                socketStreamWriter.writeUTF(value);
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private byte readByte() throws SocketException {
            try {
                return socketStreamReader.readByte();
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private void writeByte(int value) throws SocketException {
            try {
                socketStreamWriter.writeByte(value);
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }
        private short readShort() throws SocketException {
            try {
                return socketStreamReader.readShort();
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private int readInt() throws SocketException {
            try {
                return socketStreamReader.readInt();
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private void writeInt(int value) throws SocketException {
            try {
                socketStreamWriter.writeInt(value);
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private long readLong() throws SocketException {
            try {
                return socketStreamReader.readLong();
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private void writeLong(long value) throws SocketException {
            try {
                socketStreamWriter.writeLong(value);
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private int readArray(byte[] buffer, int count) throws SocketException {
            try {
                return socketInputStream.read(buffer, 0, count);
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private void writeArray(byte[] buffer, int count) throws SocketException {
            try {
                socketOutputStream.write(buffer, 0, count);
            } catch (Exception ex) {
                throw new SocketException(ex.toString());
            }
        }

        private void exitSynchronisation() throws Exception {
            try {
                if (WifiSyncServiceSettings.debugMode) {
                    ErrorHandler.logInfo("exitSync", "fails=" + syncFailedFiles.size());
                }
                if (syncFailedFiles.size() > 0 || (syncToResults != null && syncToResults.size() > 0)) {
                    syncErrorMessageId.set(R.string.syncCompletedFail);
                }
                readToEndOfCommand();
                syncPercentCompleted.set(100);
                storage.waitScanFiles();
                writeString(syncStatusOK);
            } catch (Exception ex) {
                ErrorHandler.logError("exit", ex);
                writeString(syncStatusFAIL);
            }
            flushWriter();
            clientSocket.shutdownInput();
        }

        private void getCapability() throws Exception {
            final String feature = readString();
            readToEndOfCommand();
            try {
                switch (feature) {
                    case "Build":
                        writeString("1.0.0");
                    case "API":
                        writeString("1");
                    default:
                        writeString("FALSE");
                }
                writeString(syncStatusOK);
            } catch (Exception ex) {
                ErrorHandler.logError("getCapability", ex);
                if (SocketException.class.isAssignableFrom(ex.getClass())) {
                    throw ex;
                }
                writeString(syncStatusFAIL);
            }
            flushWriter();
        }

        private void getFiles() throws Exception {
            final String folderPath = readString();
            final boolean includeSubFolders = (readByte() == 1);
            readToEndOfCommand();
            try {
                getFiles(folderPath, includeSubFolders);
                writeString(syncEndOfData);
                writeString(syncStatusOK);
            } catch (Exception ex) {
                ErrorHandler.logError("getFiles", ex, "path=" + folderPath);
                if (SocketException.class.isAssignableFrom(ex.getClass())) {
                    throw ex;
                }
                writeString(syncEndOfData);
                writeString(syncStatusFAIL + " " + ex.toString());
                syncErrorMessageId.set(R.string.syncCompletedFail);
            }
            flushWriter();
        }

        private void getFiles(String folderPath, boolean includeSubFolders) throws Exception {
            ContentResolver contentResolver = getApplicationContext().getContentResolver();
            //Uri contentUri = MediaStore.Files.getContentUri("external");
            String[] projection = new String[] {MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DATE_MODIFIED};
            int storageRootPathLength = storage.storageRootPath.length() + 1;
            String folderUrl = storage.getFileUrl(folderPath);
            String selection = "substr(" + MediaStore.Files.FileColumns.DATA + ",1," + folderUrl.length() + ") = ? AND " + MediaStore.Files.FileColumns.SIZE + " > 0";
            if (WifiSyncServiceSettings.debugMode) {
                ErrorHandler.logInfo("getFiles", "Get: " + folderPath + ",url=" + folderUrl + ", inc=" + includeSubFolders);
                /*
                try (Cursor cursor = contentResolver.query(contentUri, projection, null, null, null)) {
                    if (cursor == null) {
                        ErrorHandler.logInfo("getFiles", "no cursor");
                    } else {
                        String s = "";
                        int count = 0;
                        looper:
                        while (cursor.moveToNext()) {
                            String filePath = cursor.getString(0);
                            String ext = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                            switch (ext) {
                                case "mp3":
                                case "ogg":
                                    if (!filePath.startsWith("/storage/emulated/0/")) {
                                        count ++;
                                        s += filePath + ": " + (new Date(cursor.getLong(1) * 1000)) + "\n";
                                        if (count > 32) break looper;
                                    }
                            }
                        }
                        ErrorHandler.logInfo("getFiles", s);
                    }
                }
                */
            }
            try (Cursor cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, new String[] {folderUrl}, null)) {
                if (cursor == null) {
                    ErrorHandler.logInfo("getFiles", "no cursor");
                } else {
                    if (WifiSyncServiceSettings.debugMode) {
                        ErrorHandler.logInfo("getFiles", "count=" + cursor.getCount());
                    }
                    int urlColumnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                    int dateModifiedColumnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED);
                    while (cursor.moveToNext()) {
                        String url = cursor.getString(urlColumnIndex);
                        if (!includeSubFolders) {
                            int filenameIndex = url.lastIndexOf('/') + 1;
                            if (filenameIndex > folderUrl.length()) {
                                continue;
                            }
                        }
                        writeString(url.substring(storageRootPathLength));
                        writeLong(cursor.getLong(dateModifiedColumnIndex));
                    }
                }
            }
        }

        private void receiveFile() throws Exception {
            String filePath = readString();
            long fileLength = readLong();
            long fileDateModified = readLong();
            syncPercentCompleted.set(readShort());
            readToEndOfCommand();
            syncProgressMessage.set(String.format(getString(R.string.syncFileActionCopy), filePath));
            byte[][] buffer = new byte[2][socketReadBufferLength];
            int[] readCount = new int[2];
            AutoResetEvent waitRead = new AutoResetEvent(false);
            AutoResetEvent waitWrite = new AutoResetEvent(true);
            try {
                if (WifiSyncServiceSettings.debugMode) {
                    ErrorHandler.logInfo("receiveFile", "Receive: " + filePath);
                }
                try (OutputStream fs = storage.openWriteStream(filePath)) {
                    writeString(syncStatusOK);
                    flushWriter();
                    Thread thread = new Thread(new ReceiveFileReceiveLoop(fileLength, buffer, readCount, waitRead, waitWrite));
                    thread.start();
                    try {
                        int bytesRead;
                        int bufferIndex = 0;
                        while (true) {
                            waitRead.waitOne();
                            bytesRead = readCount[bufferIndex];
                            if (bytesRead < 0) {
                                throw new SocketException("Error reading file");
                            } else if (bytesRead == 0) {
                                break;
                            }
                            fs.write(buffer[bufferIndex], 0, bytesRead);
                            waitWrite.set();
                            bufferIndex = (bufferIndex == 1) ? 0 : 1;
                        }
                    } finally {
                        thread.interrupt();
                    }
                }
                writeString(syncStatusOK);
                flushWriter();
                storage.scanFile(filePath, fileLength, fileDateModified, FileStorageAccess.ACTION_ADD);
            } catch (Exception ex) {
                try {
                    ErrorHandler.logError("receiveFile", ex, "file=" + filePath);
                    if (SocketException.class.isAssignableFrom(ex.getClass())) {
                        throw ex;
                    }
                    syncFailedFiles.add(new FileErrorInfo(FileErrorInfo.ERROR_COPY, filePath, ex.toString()));
                    while (true) {
                        Thread.sleep(100);
                        int skipBytes = getAvailableData();
                        if (skipBytes <= 0) {
                            break;
                        }
                        for (; skipBytes > 0; skipBytes -= socketReadBufferLength) {
                            readArray(buffer[0], (skipBytes >= socketReadBufferLength) ? socketReadBufferLength : skipBytes);
                        }
                    }
                    writeString(syncStatusFAIL + " " + ex.toString());
                    flushWriter();
                } finally {
                    try {
                        storage.deleteFile(filePath);
                    } catch (Exception deleteException) {
                    }
                }
            }
        }

        private class ReceiveFileReceiveLoop implements Runnable {
            final private long fileLength;
            final private byte[][] buffer;
            final private int[] readCount;
            final private AutoResetEvent waitRead;
            final private AutoResetEvent waitWrite;

            private ReceiveFileReceiveLoop(long fileLength, byte[][] buffer, int[] readCount, AutoResetEvent waitRead, AutoResetEvent waitWrite) {
                this.fileLength = fileLength;
                this.buffer = buffer;
                this.readCount = readCount;
                this.waitRead = waitRead;
                this.waitWrite = waitWrite;
            }

            @Override
            public void run() {
                int readLength = socketReadBufferLength;
                int bytesRead;
                long remainingBytes = fileLength;
                int bufferIndex = 0;
                while (true) {
                    try {
                        if (remainingBytes <= 0) {
                            bytesRead = 0;
                        } else {
                            if (remainingBytes < socketReadBufferLength) {
                                readLength = (int) remainingBytes;
                            }
                            bytesRead = readArray(buffer[bufferIndex], readLength);
                        }
                    } catch (Exception ex) {
                        bytesRead = -1;
                        ErrorHandler.logError("receiveLoop", ex);
                    }
                    try {
                        waitWrite.waitOne();
                    } catch (InterruptedException ex) {
                        bytesRead = -1;
                    }
                    readCount[bufferIndex] = bytesRead;
                    waitRead.set();
                    if (bytesRead <= 0) {
                        break;
                    }
                    remainingBytes -= bytesRead;
                    bufferIndex = (bufferIndex == 1) ? 0 : 1;
                }
            }
        }

        private void sendFile() throws Exception {
            final String filePath = readString();
            readToEndOfCommand();
            writeString(storage.getFileUrl(filePath));
            final byte[][] buffer = new byte[2][65536];
            final int[] readCount = new int[2];
            final AutoResetEvent waitRead = new AutoResetEvent(false);
            final AutoResetEvent waitWrite = new AutoResetEvent(true);
            final Exception[] exception = new Exception[1];
            long fileLength = -1;
            long remainingBytes = 0;
            String status = syncStatusOK;
            try {
                try (InputStream fs = storage.openReadStream(filePath)) {
                    fileLength = storage.getLength(filePath);
                    writeLong(fileLength);
                    flushWriter();
                    Thread thread = new Thread(new sendFileWriteLoop(buffer, readCount, waitRead, waitWrite, exception));
                    thread.start();
                    remainingBytes = fileLength;
                    try {
                        int bytesRead;
                        int bufferIndex = 0;
                        while (true) {
                            try {
                                bytesRead = fs.read(buffer[bufferIndex], 0, 65536);
                                waitWrite.waitOne();
                            } catch (InterruptedException ex) {
                                bytesRead = -1;
                                exception[0] = ex;
                            } catch (Exception ex) {
                                ErrorHandler.logError("sendFile", ex);
                                bytesRead = -1;
                                exception[0] = ex;
                            }
                            readCount[bufferIndex] = bytesRead;
                            waitRead.set();
                            if (exception[0] != null) {
                                throw exception[0];
                            } else if (bytesRead <= 0) {
                                break;
                            }
                            remainingBytes -= bytesRead;
                            bufferIndex = (bufferIndex == 1) ? 0 : 1;
                        }
                    } finally {
                        thread.interrupt();
                    }
                }
            } catch (Exception ex) {
                ErrorHandler.logError("sendFile", ex, "file=" + filePath);
                if (SocketException.class.isAssignableFrom(ex.getClass())) {
                    throw ex;
                }
                status = syncStatusFAIL + " " + ex.toString();
                if (fileLength == -1) {
                    writeLong(0);
                } else {
                    for (; remainingBytes > 0; remainingBytes -= 65536) {
                        writeArray(buffer[0], (remainingBytes >= 65536) ? 65536 : (int) remainingBytes);
                    }
                }
            }
            writeString(status);
            flushWriter();
        }

        private class sendFileWriteLoop implements Runnable {
            final private byte[][] buffer;
            final private int[] readCount;
            final private AutoResetEvent waitRead;
            final private AutoResetEvent waitWrite;
            final private Exception[] exception;

            private sendFileWriteLoop(byte[][] buffer, int[] readCount, AutoResetEvent waitRead, AutoResetEvent waitWrite, Exception[] exception) {
                this.buffer = buffer;
                this.readCount = readCount;
                this.waitRead = waitRead;
                this.waitWrite = waitWrite;
                this.exception = exception;
            }

            @Override
            public void run() {
                int bufferIndex = 0;
                int bytesRead;
                try {
                    while (true) {
                        waitRead.waitOne();
                        bytesRead = readCount[bufferIndex];
                        if (bytesRead <= 0) {
                            break;
                        }
                        writeArray(buffer[bufferIndex], bytesRead);
                        waitWrite.set();
                        bufferIndex = (bufferIndex == 1) ? 0 : 1;
                    }
                } catch (InterruptedException ex) {
                    exception[0] = ex;
                } catch (Exception ex) {
                    exception[0] = ex;
                    ErrorHandler.logError("sendFileLoop", ex);
                } finally {
                    waitWrite.set();
                }
            }
        }

        private void showDeleteConfirmation() throws Exception {
            final int deleteCount = readInt();
            syncPercentCompleted.set(readShort());
            readToEndOfCommand();
            if (showOkCancelDialog(String.format(getString((deleteCount == 1) ? R.string.syncDeleteConfirm1 : R.string.syncDeleteConfirm9), deleteCount)) == android.R.string.ok) {
                writeString(syncStatusOK);
            } else {
                syncErrorMessageId.set(R.string.syncCancelled);
                writeString(syncStatusCANCEL);
            }
            flushWriter();
        }

        private void deleteFiles() throws Exception {
            syncPercentCompleted.set(readShort());
            readToEndOfCommand();
            String status = syncStatusOK;
            while (true) {
                String filePath = readString();
                if (filePath.length() == 0) {
                    break;
                }
                if (WifiSyncServiceSettings.debugMode) {
                    ErrorHandler.logInfo("deleteFiles", "Delete: " + filePath);
                }
                String failMessage = null;
                try {
                    syncProgressMessage.set(String.format(getString(R.string.syncFileActionDelete), filePath));
                    if (!storage.deleteFile(filePath)) {
                        status = syncStatusFAIL;
                        failMessage = getString(R.string.syncFailUnknownReason);
                    }
                    writeString(status);
                    flushWriter();
                } catch (Exception ex) {
                    ErrorHandler.logError("deleteFile", ex, "file=" + filePath);
                    if (SocketException.class.isAssignableFrom(ex.getClass())) {
                        throw ex;
                    }
                    writeString(syncStatusFAIL);
                    flushWriter();
                    failMessage = ex.toString();
                }
                if (failMessage != null) {
                    syncFailedFiles.add(new FileErrorInfo(FileErrorInfo.ERROR_DELETE, filePath, failMessage));
                }
            }
        }

        private void deleteFolders() throws Exception {
            syncPercentCompleted.set(readShort());
            readToEndOfCommand();
            String status = syncStatusOK;
            while (true) {
                String folderPath = readString();
                if (folderPath.length() == 0) {
                    break;
                }
                try {
                    if (WifiSyncServiceSettings.debugMode) {
                        ErrorHandler.logInfo("deleteFolder", "Delete: " + folderPath);
                    }
                    syncProgressMessage.set(String.format(getString(R.string.syncFileActionDelete), folderPath));
                    if (!storage.deleteFolder(folderPath)) {
                        status = syncStatusFAIL;
                    }
                } catch (Exception ex) {
                    ErrorHandler.logError("deleteFolder", ex, "path=" + folderPath);
                    if (SocketException.class.isAssignableFrom(ex.getClass())) {
                        throw ex;
                    }
                    status = syncStatusFAIL;
                }
            }
            writeString(status);
            flushWriter();
        }

        private int showOkCancelDialog(final String prompt) {
            try {
                resultsActivityReady.waitOne();
                WifiSyncApp app = (WifiSyncApp) getApplicationContext();
                return Dialog.showOkCancel(app.currentActivity, prompt);
            } catch (Exception ex) {
                ErrorHandler.logError("showOkCancel", ex);
                return android.R.string.cancel;
            }
        }

        private void sendPlaylists() throws Exception {
            String playlistsFolderPath = readString();
            readToEndOfCommand();
            try {
                ArrayList<FileInfo> files = null;
                switch (settingsReverseSyncPlayer) {
                    case WifiSyncServiceSettings.PLAYER_GONEMAD:
                        files = storage.getFiles(playlistsFolderPath);
                        if (!playlistsFolderPath.equalsIgnoreCase(settingsReverseSyncPlaylistsPath)) {
                            files.addAll(storage.getFiles(settingsReverseSyncPlaylistsPath));
                        }
                        break;
                    case WifiSyncServiceSettings.PLAYER_POWERAMP:
                        files = new ArrayList<>();
                        break;
                }
                for (FileInfo info : files) {
                    writeString(info.filename);
                    writeLong(info.dateModified);
                }
                writeString(syncEndOfData);
                writeString(syncStatusOK);
            } catch (Exception ex) {
                ErrorHandler.logError("sendPlaylists", ex);
                if (SocketException.class.isAssignableFrom(ex.getClass())) {
                    throw ex;
                }
                setPreviewFailed();
                writeString(syncEndOfData);
                writeString(syncStatusFAIL + " " + ex.toString());
            }
            flushWriter();
        }

        private void sendStats() throws Exception {
            readToEndOfCommand();
            try {
                FileStatsMap cachedStatsLookup;
                File statsCacheFile = new File(getFilesDir(), "CachedStats.dat");
                boolean statsCachedFileExists = (statsCacheFile.exists());
                if (!statsCachedFileExists) {
                    cachedStatsLookup = new FileStatsMap(0);
                } else {
                    try (FileInputStream stream = new FileInputStream(statsCacheFile)) {
                        try (BufferedInputStream bufferedStream = new BufferedInputStream(stream, 4096)) {
                            try (DataInputStream reader = new DataInputStream(bufferedStream)) {
                                int count = reader.readInt();
                                cachedStatsLookup = new FileStatsMap(count);
                                for (int index = 0; index < count; index++) {
                                    String filename = reader.readUTF();
                                    byte rating = reader.readByte();
                                    long lastPlayedDate = reader.readLong();
                                    int playCount = reader.readInt();
                                    cachedStatsLookup.put(filename, new FileStatsInfo(filename, rating, lastPlayedDate, playCount));
                                }
                            }}}
                }
                ArrayList<FileStatsInfo> latestStats = null;
                switch (settingsReverseSyncPlayer) {
                    case WifiSyncServiceSettings.PLAYER_GONEMAD:
                        latestStats = loadGoneMadStats();
                        if (latestStats == null) {
                            syncErrorMessageId.set(R.string.errorSyncGoneMadTrial);
                        }
                        break;
                    case WifiSyncServiceSettings.PLAYER_POWERAMP:
                        latestStats = loadPowerAmpStats();
                        break;
                }
                if (latestStats == null) {
                    setPreviewFailed();
                    ErrorHandler.logError("sendStats", "Unable to retrieve stats for player: " + settingsReverseSyncPlayer);
                    writeString(syncEndOfData);
                    writeString(syncStatusFAIL + " Unable to retrieve stats");
                } else {
                    for (FileStatsInfo latestStatsInfo : latestStats) {
                        int incrementatalPlayCount;
                        boolean ratingChanged;
                        FileStatsInfo cachedStatsInfo = cachedStatsLookup.get(latestStatsInfo.fileUrl);
                        if (cachedStatsInfo == null) {
                            if (!settingsReverseSyncRatings) {
                                ratingChanged = false;
                                latestStatsInfo.rating = 0;
                            } else {
                                ratingChanged = (statsCachedFileExists && latestStatsInfo.rating > 0);
                            }
                            if (!settingsReverseSyncPlayCounts) {
                                latestStatsInfo.playCount = 0;
                                latestStatsInfo.lastPlayedDate = 0;
                                incrementatalPlayCount = 0;
                            } else {
                                incrementatalPlayCount = latestStatsInfo.playCount;
                            }
                        } else {
                            if (!settingsReverseSyncRatings) {
                                ratingChanged = false;
                                latestStatsInfo.rating = cachedStatsInfo.rating;
                            } else {
                                ratingChanged = (latestStatsInfo.rating > 0 && (latestStatsInfo.rating != cachedStatsInfo.rating));
                            }
                            if (!settingsReverseSyncPlayCounts) {
                                latestStatsInfo.playCount = cachedStatsInfo.playCount;
                                latestStatsInfo.lastPlayedDate = cachedStatsInfo.lastPlayedDate;
                                incrementatalPlayCount = 0;
                            } else {
                                incrementatalPlayCount = latestStatsInfo.playCount - cachedStatsInfo.playCount;
                            }
                        }
                        if (ratingChanged || incrementatalPlayCount > 0) {
                            writeString(latestStatsInfo.fileUrl);
                            writeByte((!settingsReverseSyncRatings || !ratingChanged) ? 0 : latestStatsInfo.rating);
                            writeLong((!settingsReverseSyncPlayCounts) ? 0 : latestStatsInfo.lastPlayedDate);
                            writeInt((!settingsReverseSyncPlayCounts || incrementatalPlayCount <= 0) ? 0 : incrementatalPlayCount);
                        }
                    }
                    if (!settingsSyncPreview) {
                        try (FileOutputStream stream = new FileOutputStream(statsCacheFile)) {
                            try (BufferedOutputStream bufferedStream = new BufferedOutputStream(stream, 4096)) {
                                try (DataOutputStream writer = new DataOutputStream(bufferedStream)) {
                                    int count = latestStats.size();
                                    writer.writeInt(count);
                                    for (int index = 0; index < count; index++) {
                                        FileStatsInfo stats = latestStats.get(index);
                                        writer.writeUTF(stats.fileUrl);
                                        writer.writeByte(stats.rating);
                                        writer.writeLong(stats.lastPlayedDate);
                                        writer.writeInt(stats.playCount);
                                    }
                                    writer.flush();
                                }}}
                    }
                    writeString(syncEndOfData);
                    writeString(syncStatusOK);
                }
            } catch (Exception ex) {
                ErrorHandler.logError("sendStats", ex);
                if (SocketException.class.isAssignableFrom(ex.getClass())) {
                    throw ex;
                }
                setPreviewFailed();
                writeString(syncEndOfData);
                writeString(syncStatusFAIL + " " + ex.toString());
            }
            flushWriter();
        }

        private ArrayList<FileStatsInfo> loadPowerAmpStats() {
            String storageRootPath = storage.storageRootPath;
            ArrayList<FileStatsInfo> results = new ArrayList<>();
            ContentResolver contentResolver = getContentResolver();
            String[] projection = {"folders.path", "folder_files.name", "folder_files.rating", "folder_files.played_times", "folder_files.played_at"};
            try (Cursor cursor = contentResolver.query(Uri.parse("content://com.maxmpz.audioplayer.data/files"), projection,null,null,null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String filePath = cursor.getString(0) + cursor.getString(1);
                        if (filePath.regionMatches(true, 0, storageRootPath, 0, storageRootPath.length())) {
                            filePath = filePath.substring(storageRootPath.length() + 1);
                        }
                        byte rating = (byte) (cursor.getInt(2) * 20);
                        int playCount = cursor.getInt(3);
                        long lastPlayed = cursor.getLong(4);
                        results.add(new FileStatsInfo(filePath, rating, lastPlayed, playCount));
                    }
                }
            }
            return results;
        }

        private ArrayList<FileStatsInfo> loadGoneMadStats() throws Exception {
            File statsFile = new File(FileStorageAccess.getSdCardFromIndex(getApplicationContext(), 0).getPath(),"gmmp/stats.xml");
            if (!statsFile.exists()) {
                try (FileOutputStream stream = new FileOutputStream(statsFile)) {
                    stream.write(0);
                }
            }
            final Object fileWriteWait = new Object();
            final AtomicBoolean fileWriteStarted = new AtomicBoolean(false);
            final AtomicBoolean fileWriteCompleted = new AtomicBoolean(false);
            FileObserver observer = new FileObserver(statsFile.getPath()) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    switch (event) {
                        case FileObserver.OPEN:
                            fileWriteStarted.set(true);
                            break;
                        case FileObserver.CLOSE_WRITE:
                            synchronized (fileWriteWait) {
                                fileWriteCompleted.set(true);
                                fileWriteWait.notifyAll();
                            }
                            break;
                    }
                }
            };
            observer.startWatching();
            try {
                Intent serviceIntent = new Intent();
                serviceIntent.setComponent(new ComponentName("gonemad.gmmp", "gonemad.gmmp.receivers.BackupReceiver"));
                serviceIntent.setAction("gonemad.gmmp.action.BACKUP_STATS");
                sendBroadcast(serviceIntent);
                synchronized (fileWriteWait) {
                    fileWriteWait.wait(10000);
                    if (!fileWriteStarted.get()) {
                        return null;
                    } else if (!fileWriteCompleted.get()) {
                        fileWriteWait.wait(60000);
                    }
                }
            } finally {
                observer.stopWatching();
            }
            try (FileInputStream stream = new FileInputStream(statsFile)) {
                GmmpStatsXmlHandler handler = new GmmpStatsXmlHandler(storage.storageRootPath);
                SAXParserFactory.newInstance().newSAXParser().parse(stream, handler);
                return handler.stats;
            }
        }

        private class GmmpStatsXmlHandler extends DefaultHandler {
            ArrayList<FileStatsInfo> stats = new ArrayList<>();
            private String lastName;
            private String lastFileUrl = "";
            private byte lastRating;
            private long lastPlayedDate;
            private int lastPlayCount;
            final String storageRootPath;

            GmmpStatsXmlHandler(String storageRootPath) {
                this.storageRootPath = storageRootPath;
            }

            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                lastName = qName;
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (qName.equalsIgnoreCase("File")) {
                    if (lastFileUrl.regionMatches(true, 0, storageRootPath, 0, storageRootPath.length())) {
                        lastFileUrl = lastFileUrl.substring(storageRootPath.length() + 1);
                    }
                    stats.add(new FileStatsInfo(lastFileUrl, lastRating, lastPlayedDate, lastPlayCount));
                    lastFileUrl = "";
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (lastName.equalsIgnoreCase("Uri")) {
                    lastFileUrl += new String(ch, start, length);
                } else if (lastName.equalsIgnoreCase("Rating")) {
                    lastRating = (byte) (Float.valueOf(new String(ch, start, length)) * 20);
                } else if (lastName.equalsIgnoreCase("LastPlayed")) {
                    lastPlayedDate = (Long.valueOf(new String(ch, start, length)));
                } else if (lastName.equalsIgnoreCase("Playcount")) {
                    lastPlayCount = Integer.valueOf(new String(ch, start, length));
                }
            }
        }

        private class FileStatsMap extends HashMap<String, FileStatsInfo> {
            FileStatsMap(int initialCapacity) {
                super(initialCapacity);
            }

            @Override
            public FileStatsInfo put(String key, FileStatsInfo value) {
                return super.put(key.toLowerCase(Locale.ROOT), value);
            }

            FileStatsInfo get(String key) {
                return super.get(key.toLowerCase(Locale.ROOT));
            }

            boolean containsKey(String key) {
                return super.containsKey(key.toLowerCase(Locale.ROOT));
            }
        }

        private void getReverseSyncPreviewResults() throws Exception {
            syncToResults = null;
            ArrayList<SyncResultsInfo> results = new ArrayList<>();
            String status;
            try {
                readToEndOfCommand();
                while (true) {
                    String action = readString();
                    if (action.length() == 0) {
                        break;
                    }
                    String targetName = readString();
                    byte alert = readByte();
                    String message = readString();
                    results.add(new SyncResultsInfo(action, targetName, alert, message));
                }
                status = syncStatusOK;
            } catch (Exception ex) {
                ErrorHandler.logError("getPreview", ex);
                if (SocketException.class.isAssignableFrom(ex.getClass())) {
                    throw ex;
                }
                syncErrorMessageId.set(R.string.errorSyncNonSpecific);
                results = null;
                status = syncStatusFAIL + " " + ex.toString();
            }
            writeString(status);
            flushWriter();
            syncToResults = results;
        }

        private void showSyncPreview() throws Exception {
            syncFromResults = null;
            final ArrayList<SyncResultsInfo> results = new ArrayList<>();
            try {
                readToEndOfCommand();
                readLong();  //deltaSpace =
                readLong();  //estimatedBytesSendToDevice =
                while (true) {
                    String action = readString();
                    if (action.length() == 0) {
                        break;
                    }
                    String estimatedSize = readString();
                    String targetFilename = readString();
                    results.add(new SyncResultsInfo(action, targetFilename, estimatedSize));
                }
                showSyncPreviewResults(results, syncStatusOK);
            } catch (Exception ex) {
                ErrorHandler.logError("showPreview", ex);
                if (SocketException.class.isAssignableFrom(ex.getClass())) {
                    throw ex;
                }
                syncErrorMessageId.set(R.string.errorSyncNonSpecific);
                showSyncPreviewResults(null, syncStatusFAIL + " " + ex.toString());
            }
        }

        private void showSyncPreviewResults(ArrayList<SyncResultsInfo> results, String status) throws Exception {
            writeString(status);
            flushWriter();
            clientSocket.shutdownInput();
            syncFromResults = results;
            waitSyncResults.set();
        }
    }

    static ArrayList<String> getMusicBeePlaylists() throws Exception {
        final ArrayList<String> playlists = new ArrayList<>();
        InetAddress address = InetAddress.getByName(WifiSyncServiceSettings.defaultIpAddressValue);
        try (Socket clientSocket = new Socket()) {
            clientSocket.connect(new InetSocketAddress(address, serverPort), socketConnectTimeout);
            try (InputStream socketInputStream = clientSocket.getInputStream()) {
            try (DataInputStream socketStreamReader = new DataInputStream((socketInputStream))) {
            try (OutputStream socketOutputStream = clientSocket.getOutputStream()) {
            try (DataOutputStream socketStreamWriter = new DataOutputStream(socketOutputStream)) {
                clientSocket.setSoTimeout(socketReadTimeout);
                String hello = socketStreamReader.readUTF();
                if (hello.startsWith(serverHelloPrefix)) {
                    socketStreamWriter.writeUTF(clientHelloVersion);
                    socketStreamWriter.writeUTF("GetPlaylists");
                    socketStreamWriter.writeUTF(syncEndOfData);
                    socketStreamWriter.flush();
                    while (true) {
                        String playlistName = socketStreamReader.readUTF();
                        if (playlistName.length() == 0) {
                            break;
                        }
                        playlists.add(playlistName);
                    }
                }
            }}}}
        }
        return playlists;
    }

    @Nullable
    static String getMusicBeeServerAddress() {
        ArrayList<CandidateIpAddress> candidateAddresses = findCandidateIpAddresses();
        if (candidateAddresses.size() == 0) {
            return null;
        } else {
            CandidateIpAddress candidate = candidateAddresses.get(0);
            return (!candidate.syncConfigMatched) ? "FAIL": candidate.toString();
        }
    }

    static ArrayList<CandidateIpAddress> findCandidateIpAddresses() {
        ArrayList<CandidateIpAddress> candidateAddresses = new ArrayList<>();
        Enumeration<NetworkInterface> ni;
        try {
            HashSet<String> subnetLookup = new HashSet<>();
            ni = NetworkInterface.getNetworkInterfaces();
            while (ni.hasMoreElements()) {
                NetworkInterface networkInterface = ni.nextElement();
                try {
                    if (networkInterface.isUp()) {
                        Enumeration<InetAddress> niAddresses = networkInterface.getInetAddresses();
                        while (niAddresses.hasMoreElements()) {
                            InetAddress address = niAddresses.nextElement();
                            if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                                byte[] baseAddressValue = address.getAddress();
                                String subnet = (baseAddressValue[0] & 0xFF) + "." + (baseAddressValue[1] & 0xFF) + "." + (baseAddressValue[2] & 0xFF) + ".";
                                if (WifiSyncServiceSettings.debugMode) {
                                    ErrorHandler.logInfo("locate", "search=" + subnet);
                                }
                                if (subnetLookup.add(subnet)) {
                                    int excludeIndex = (baseAddressValue[3] & 0xFF) - 1;
                                    if (WifiSyncServiceSettings.debugMode) {
                                        ErrorHandler.logInfo("locate", "exclude=" + excludeIndex);
                                    }
                                    AutoResetEvent waitLock = new AutoResetEvent(false);
                                    AtomicInteger scannedCount = new AtomicInteger(0);
                                    int threadCount = 254;
                                    Thread[] thread = new Thread[threadCount];
                                    for (int index = 0; index < threadCount; index++) {
                                        if (index != excludeIndex) {
                                            thread[index] = new Thread(new ServerPinger(InetAddress.getByName(subnet + (1 + index)), waitLock, scannedCount, candidateAddresses));
                                            thread[index].start();
                                        }
                                    }
                                    waitLock.waitOne();
                                    for (int index = 0; index < threadCount; index++) {
                                        if (index != excludeIndex) {
                                            thread[index].interrupt();
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    ErrorHandler.logError("locate", ex);
                }
            }
        } catch (Exception ex) {
            ErrorHandler.logError("locate", ex);
        }
        return candidateAddresses;
    }

    static class ServerPinger implements Runnable {
        private boolean connected = false;
        final private InetAddress address;
        final private AtomicInteger scannedCount;
        final private AutoResetEvent waitLock;
        final private ArrayList<CandidateIpAddress> candidateAddresses;

        private ServerPinger(InetAddress address) {
            this.address = address;
            this.scannedCount = new AtomicInteger();
            this.waitLock = null;
            this.candidateAddresses = null;
        }

        private ServerPinger(InetAddress address, AutoResetEvent waitLock, AtomicInteger scannedCount, ArrayList<CandidateIpAddress> candidateAddresses) {
            this.address = address;
            this.scannedCount = scannedCount;
            this.waitLock = waitLock;
            this.candidateAddresses = candidateAddresses;
        }

        static boolean ping() {
            try {
                ServerPinger pinger = new ServerPinger(InetAddress.getByName(WifiSyncServiceSettings.defaultIpAddressValue));
                pinger.run();
                return pinger.connected;
            } catch (Exception ex) {
                return false;
            }
        }

        @Override
        public void run() {
            try {
                try (Socket clientSocket = new Socket()) {
                    clientSocket.connect(new InetSocketAddress(address, serverPort), socketConnectTimeout);
                    if (WifiSyncServiceSettings.debugMode) {
                        ErrorHandler.logInfo("ping", "socket ok=" + address.toString());
                    }
                    try {
                        try (InputStream socketInputStream = clientSocket.getInputStream()) {
                        try (DataInputStream socketStreamReader = new DataInputStream((socketInputStream))) {
                        try (OutputStream socketOutputStream = clientSocket.getOutputStream()) {
                        try (DataOutputStream socketStreamWriter = new DataOutputStream(socketOutputStream)) {
                            clientSocket.setSoTimeout(socketReadTimeout);
                            String hello = socketStreamReader.readUTF();
                            if (WifiSyncServiceSettings.debugMode) {
                                ErrorHandler.logInfo("ping", "hello=" + hello);
                            }
                            if (hello.startsWith(serverHelloPrefix)) {
                                socketStreamWriter.writeUTF(clientHelloVersion);
                                socketStreamWriter.writeUTF("Ping");
                                socketStreamWriter.writeByte(0);
                                socketStreamWriter.writeUTF(WifiSyncServiceSettings.deviceName);
                                socketStreamWriter.writeByte(WifiSyncServiceSettings.deviceStorageIndex);
                                socketStreamWriter.writeUTF(syncEndOfData);
                                socketStreamWriter.flush();
                                String status = socketStreamReader.readUTF();
                                connected = true;
                                if (WifiSyncServiceSettings.debugMode) {
                                    ErrorHandler.logInfo("ping", "matched=" + address.toString() + ",status=" + status);
                                }
                                if (candidateAddresses != null) {
                                    synchronized (candidateAddresses) {
                                        candidateAddresses.add(new CandidateIpAddress((Inet4Address) address, status.equals("OK")));
                                        waitLock.set();
                                    }
                                }
                            }
                        }}}}
                    } catch (Exception ex) {
                        ErrorHandler.logError("ping", ex);
                    }
                }
            } catch (Exception ex) {
            }
            finally {
                if (candidateAddresses != null) {
                    if (scannedCount.incrementAndGet() == 253) {
                        waitLock.set();
                    }
                }
            }
        }
    }

    private static class CandidateIpAddress {
        final Inet4Address address;
        final boolean syncConfigMatched;

        CandidateIpAddress(Inet4Address address, boolean syncConfigMatched) {
            this.address = address;
            this.syncConfigMatched = syncConfigMatched;
        }

        @Override @NonNull
        public String toString() {
            return address.toString().substring(1);
        }
    }

    private class FileStatsInfo {
        final String fileUrl;
        byte rating;
        long lastPlayedDate;
        int playCount;

        FileStatsInfo(String fileUrl, byte rating, long lastPlayedDate, int playCount) {
            this.fileUrl = fileUrl;
            this.rating = rating;
            this.lastPlayedDate = lastPlayedDate;
            this.playCount = playCount;
        }
    }
}

class AutoResetEvent {
    private final Object monitor = new Object();
    private volatile boolean open;

    AutoResetEvent(boolean open) {
        this.open = open;
    }

    void set() {
        synchronized (monitor) {
            open = true;
            monitor.notifyAll();
        }
    }

    void reset() {
        open = false;
    }

    void waitOne() throws InterruptedException {
        synchronized (monitor) {
            while (!open) {
                monitor.wait();
            }
            reset();
        }
    }
}

class StorageCategory {
    static final int UNKNOWN = 0;
    static final int INTERNAL = 1;
}

class FileStorageAccess {
    private final Context context;
    private final ContentResolver contentResolver;
    final String storageRootPath;
    private final Uri storageRootPermissionedUri;
    private final String rootId;
    private final boolean isDocumentFileStorage;
    private final AtomicInteger fileScanCount = new AtomicInteger(0);
    private final Object fileScanWait = new Object();
    private ArrayList<FileInfo> updateFiles = new ArrayList<>();
    private ArrayList<String> deleteFileUrls = new ArrayList<>();
    private ArrayList<FileInfo> updatePlaylists = new ArrayList<>();
    private ArrayList<String> deletePlaylistUrls = new ArrayList<>();
    static final int ACTION_ADD = 0;
    static final int ACTION_DELETE = 1;

    FileStorageAccess(Context context, String storageRootPath, Uri storageRootPermissionedUri) {
        this.context = context;
        contentResolver = context.getContentResolver();
        this.storageRootPath = storageRootPath;
        this.storageRootPermissionedUri = storageRootPermissionedUri;
        isDocumentFileStorage = (storageRootPermissionedUri != null);
        if (!isDocumentFileStorage) {
            rootId = null;
        } else {
            String segment = storageRootPermissionedUri.getLastPathSegment();
            rootId = segment.substring(0, segment.indexOf(':') + 1);
        }
        if (WifiSyncServiceSettings.debugMode) {
            ErrorHandler.logInfo("storage", "path=" + storageRootPath + ",root=" + ((rootId == null) ? "null" : rootId + ",uri=" + storageRootPermissionedUri.toString()));
        }
    }

    ArrayList<FileInfo> getFiles(String folderPath) {
        ArrayList<FileInfo> files = new ArrayList<>();
        if (isDocumentFileStorage) {
            Uri folderUri = DocumentsContract.buildChildDocumentsUriUsingTree(storageRootPermissionedUri, getDocumentId(folderPath));
            try (Cursor cursor = contentResolver.query(folderUri, new String[] {"document_id"},null,null,null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String documentId = cursor.getString(0);
                        DocumentFile file = DocumentFile.fromSingleUri(context, DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId));
                        if (file.isFile()) {
                            String fileUrl = file.getUri().toString();
                            files.add(new FileInfo(folderPath + fileUrl.substring(fileUrl.lastIndexOf("%2F") + 3), file.lastModified() / 1000));
                        }
                    }
                }
            }
        } else {
            File folder = new File(getFileUrl(folderPath));
            File[] folderFiles = folder.listFiles();
            if (folderFiles != null) {
                final int storageRootPathLength = storageRootPath.length() + 1;
                for (File file : folderFiles) {
                    if (file.isFile()) {
                        files.add(new FileInfo(file.getPath().substring(storageRootPathLength), file.lastModified() / 1000));
                    }
                }
            }
        }
        return files;
    }

/*
    boolean fileExists(String filePath) {
        if (isDocumentFileStorage) {
            return documentFileExists(getDocumentFileUri(filePath));
        } else {
            return new File(getFileUrl(filePath)).exists();
        }
    }
*/

    FileInputStream openReadStream(String filePath) throws Exception {
        if (isDocumentFileStorage) {
            Uri fileUri = createDocumentFile(filePath);
            return contentResolver.openAssetFileDescriptor(fileUri, "r", null).createInputStream();
        } else {
            return new FileInputStream(new File(getFileUrl(filePath)));
        }
    }

    FileOutputStream openWriteStream(String filePath) throws Exception {
        if (isDocumentFileStorage) {
            Uri fileUri = createDocumentFile(filePath);
            return contentResolver.openAssetFileDescriptor(fileUri, "wt", null).createOutputStream();
        } else {
            File file = new File(getFileUrl(filePath));
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                ErrorHandler.logError("openWriteStream", "Unable to create folder: " + parent.getPath());
                throw new FileNotFoundException();
            }
            return new FileOutputStream(file);
        }
    }

    long getLength(String filePath) {
        if (isDocumentFileStorage) {
            return DocumentFile.fromSingleUri(context, getDocumentFileUri(filePath)).length();
        } else {
            return new File(getFileUrl(filePath)).length();
        }
    }

    private Uri createDocumentFile(String filePath) throws Exception {
        int charIndex = filePath.lastIndexOf('/');
        if (charIndex <= 0) {
            String message =  "Invalid filename: " + filePath;
            ErrorHandler.logError("createFile", message);
            throw new InvalidObjectException(message);
        }
        final String mime = "application/octet-stream";
        final String name = filePath.substring(charIndex + 1);
        Uri fileUri = getDocumentFileUri(filePath);
        //DocumentFile file = DocumentFile.fromSingleUri(context, fileUri);
        //if (file.exists()) {
        if (documentFileExists(fileUri)) {
            return fileUri;
        }
        else {
            String parentPath = filePath.substring(0, charIndex);
            Uri folderUri = createDocumentFolder(parentPath);
            try {
                return DocumentsContract.createDocument(contentResolver, folderUri, mime, name);
                //if (!isPlaylistFile(filePath)) {
                //    expectedNewFileCount ++;
                //}
                //return uri;
            } catch (Exception ex) {
                ErrorHandler.logError("createFile", filePath + ": " + ex.toString() + ", folder=" + ((folderUri == null) ? "null" : folderUri.toString()));
                throw ex;
            }
        }
    }

    private Uri createDocumentFolder(String folderPath) throws Exception {
        Uri folderUri = getDocumentFileUri(folderPath);
        //DocumentFile folder = DocumentFile.fromSingleUri(context, folderUri);
        //if (folder.exists()) {
        if (documentFileExists(folderUri)) {
            return folderUri;
        }
        else {
            int charIndex = folderPath.lastIndexOf('/');
            String parentPath;
            if (charIndex == -1) {
                parentPath = "";
            } else {
                parentPath = folderPath.substring(0, charIndex);
                createDocumentFolder(parentPath);
            }
            try {
                return DocumentsContract.createDocument(contentResolver, getDocumentFileUri(parentPath), DocumentsContract.Document.MIME_TYPE_DIR, folderPath.substring(charIndex + 1));
            } catch (Exception ex) {
                ErrorHandler.logError("createFolder", folderPath + ": " + ex.toString());
                throw ex;
            }
        }
    }

    private boolean documentFileExists(Uri fileUri) {
        try (Cursor cursor = contentResolver.query(fileUri, new String[] {DocumentsContract.Document.COLUMN_DOCUMENT_ID}, null, null, null)) {
            if (cursor == null) {
                return false;
            } else {
                return (cursor.getCount() > 0);
            }
        } catch (IllegalArgumentException ex) {
            // ignore file not found errors
        } catch (Exception ex) {
            // not completely comfortable ignoring if this was to happen, but it replicates the Android implementation
            if (BuildConfig.DEBUG) ErrorHandler.logInfo("exists", fileUri.toString() + ": " + ex.toString());
        }
        return false;
    }

/*
    boolean setLastModified(String filePath, long time)
    {
        String fileUrl = getFileUrl(filePath);
        if (!isDocumentFileStorage) {
            new File(fileUrl).setLastModified(time);
        }
        ContentValues updateValues = new ContentValues();
        updateValues.put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, time);
        int updated = context.getContentResolver().update(file.getUri(), updateValues, null, null);
        return updated == 1;
    }
*/

    boolean deleteFile(String filePath) throws Exception {
        String fileUrl = getFileUrl(filePath);
        if (isDocumentFileStorage) {
            Uri fileUri = getDocumentFileUri(filePath);
            try {
                if (!DocumentsContract.deleteDocument(contentResolver, fileUri)) {
                    return false;
                } else {
                    scanFile(filePath, 0, 0, ACTION_DELETE);
                    return true;
                }
            } catch (Exception ex) {
                if (documentFileExists(fileUri)) {
                    throw ex;
                } else {
                    return true;
                }
            }
        } else {
            File file = new File(fileUrl);
            if (file.delete()) {
                scanFile(filePath, 0, 0, ACTION_DELETE);
                return true;
            } else {
                return !file.exists();
            }
        }
    }

    boolean deleteFolder(String folderPath) throws Exception {
        String folderUrl = getFileUrl(folderPath);
        if (isDocumentFileStorage) {
            Uri folderUri = getDocumentFileUri(folderPath);
            try {
                return DocumentsContract.deleteDocument(contentResolver, folderUri);
            } catch (Exception ex) {
                if (documentFileExists(folderUri)) {
                    throw ex;
                } else {
                    return true;
                }
            }
        } else {
            File folder = new File(folderUrl);
            if (!deleteFolderFiles(folder)) {
                return false;
            } else if (folder.delete()) {
                scanFile(folderPath, 0, 0, ACTION_DELETE);
                return true;
            } else {
                return !folder.exists();
            }
        }
    }

    private boolean deleteFolderFiles(File folder) {
        for (File file : folder.listFiles()) {
            if (file.isDirectory()) {
                deleteFolderFiles(file);
            }
            if (!file.delete()) {
                return false;
            }
            scanFile(file.getPath(), 0, 0, ACTION_DELETE);
        }
        return true;
    }

    private boolean isPlaylistFile(String filePath) {
        int charIndex = filePath.lastIndexOf('.');
        if (charIndex != -1) {
            String ext = filePath.substring(charIndex);
            return (ext.equalsIgnoreCase(".m3u") || ext.equalsIgnoreCase(".m3u8"));
        }
        return false;
    }

    String getFileUrl(String filePath) {
        if (filePath.regionMatches(true, 0, storageRootPath, 0, storageRootPath.length())) {
            return filePath;
        } else {
            if (filePath.startsWith("/")) {
                filePath = filePath.substring(1);
            }
            if (storageRootPath.endsWith("/")) {
                return storageRootPath + filePath;
            } else {
                return storageRootPath + "/" + filePath;
            }
        }
    }

    private String getDocumentId(String filePath) {
        if (!filePath.startsWith("/")) {
            return rootId + filePath;
        } else {
            return rootId + filePath.substring(1);
        }
    }

    private Uri getDocumentFileUri(String filePath) {
        return DocumentsContract.buildDocumentUriUsingTree(storageRootPermissionedUri, getDocumentId(filePath));
    }

    void scanFile(String filePath, long fileLength, long fileDateModified, int action) {
        String fileUrl = getFileUrl(filePath);
        if (isPlaylistFile(filePath)) {
            if (action == ACTION_DELETE) {
                deletePlaylistUrls.add(filePath);
            } else {
                updatePlaylists.add(new FileInfo(filePath, fileLength, fileDateModified));
            }
        } else if (isDocumentFileStorage) {
/*
            if (action == ACTION_DELETE) {
                deleteFileUrls.add(filePath);
            } else {
                updateFiles.add(new FileInfo(filePath, fileLength, fileDateModified));
            }
*/
        } else {
            fileScanCount.getAndIncrement();
            MediaScannerConnection.scanFile(context, new String[] {fileUrl}, null, new MediaScannerConnection.OnScanCompletedListener() {
                public void onScanCompleted(String path, Uri uri) {
                    fileScanCount.getAndDecrement();
                    synchronized (fileScanWait) {
                        fileScanWait.notifyAll();
                    }
                }
            });
        }
    }

    void waitScanFiles() {
        if (WifiSyncServiceSettings.debugMode) {
            ErrorHandler.logInfo("waitScanFiles", "start");
        }
        try {
            if (isDocumentFileStorage) {
                // there isnt any API I know of to determine if the media-scanner is still running for document storage
                String[] projection = new String[] {"count(*)"};
                int lastDatabaseFileCount = -1;
                for (int retryCount = 0; retryCount < 30; retryCount ++) {
                    int count = -1;
                    try (Cursor cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, null, null, null)) {
                        if (cursor != null) {
                            cursor.moveToFirst();
                            count = cursor.getInt(0);
                        }
                    }
                    if (count == lastDatabaseFileCount) {
                        break;
                    }
                    lastDatabaseFileCount = count;
                    Thread.sleep(1000);
                }
            } else {
                while (fileScanCount.get() > 0) {
                    synchronized (fileScanWait) {
                        fileScanWait.wait();
                    }
                }
            }
        } catch (Exception ex) {
            ErrorHandler.logError("waitScan", ex);
        }
        if (deletePlaylistUrls.size() > 0) {
            try {
                String[] projection = new String[] {MediaStore.Audio.Playlists._ID};
                String selection = MediaStore.Audio.Playlists.DATA + " = ?";
                for (String filePath : deletePlaylistUrls) {
                    String playlistUrl = getFileUrl(filePath);
                    long playlistId = 0;
                    try (Cursor cursor = contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, projection, selection, new String[] {playlistUrl}, null)) {
                        if (cursor != null && cursor.moveToFirst()) {
                            playlistId = cursor.getLong(0);
                        }
                    }
                    if (playlistId != 0) {
                        contentResolver.delete(MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId), null, null);
                    }
                    contentResolver.delete(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, MediaStore.Audio.Playlists.DATA + " = ?", new String[] {playlistUrl});
                }
            } catch (Exception ex) {
                ErrorHandler.logError("deletePlaylists", ex);
            }
        }
        if (updatePlaylists.size() > 0) {
            try {
                CaseInsensitiveMap fileIdLookup = new CaseInsensitiveMap();
                String[] projection = new String[] {MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA};
                String selection = "substr(" + MediaStore.Files.FileColumns.DATA + ",1," + storageRootPath.length() + ") = ?"; // AND " + MediaStore.Files.FileColumns.MEDIA_TYPE + " = " + MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO;
                try (Cursor cursor = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, new String[] {storageRootPath}, null)) {
                    if (cursor != null) {
                        int idColumnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
                        int urlColumnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA);
                        while (cursor.moveToNext()) {
                            fileIdLookup.put(cursor.getString(urlColumnIndex), cursor.getString(idColumnIndex));
                        }
                    }
                }
                for (FileInfo fileInfo : updatePlaylists) {
                    String playlistUrl = null;
                    URI playlistFolderPath = null;
                    String playlistFileUrl = null;
                    try {
                        String filePath = fileInfo.filename;
                        long currentTime = System.currentTimeMillis() / 1000;
                        long fileDateModified = fileInfo.dateModified;
                        playlistUrl = getFileUrl(filePath);
                        playlistFolderPath = new URI(playlistUrl.substring(0, playlistUrl.lastIndexOf('/') + 1));
                        playlistFileUrl = null;
                        if (!isDocumentFileStorage) {
                            new File(playlistUrl).setLastModified(fileDateModified * 1000);
                        }
                        ContentValues values = new ContentValues();
                        values.put(MediaStore.Audio.Playlists.DATE_MODIFIED, fileDateModified);
                        int updatedCount = contentResolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values, MediaStore.Audio.Playlists.DATA + " = ?", new String[] {playlistUrl});
                        if (updatedCount == 0) {
                            int charIndex = filePath.lastIndexOf('.');
                            if (charIndex != -1) {
                                String ext = filePath.substring(charIndex);
                                charIndex = filePath.lastIndexOf('/');
                                String name = filePath.substring(charIndex + 1, filePath.length() - ext.length());
                                values = new ContentValues();
                                values.put(MediaStore.Audio.Playlists.DATA, playlistUrl);
                                values.put(MediaStore.Audio.Playlists.NAME, name);
                                values.put(MediaStore.Audio.Playlists.DATE_MODIFIED, fileDateModified);
                                values.put(MediaStore.Audio.Playlists.DATE_ADDED, currentTime);
                                contentResolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values);
                            }
                        }
                        projection = new String[] {MediaStore.Audio.Playlists._ID};
                        selection = MediaStore.Audio.Playlists.DATA + " = ?";
                        long playlistId = 0;
                        try (Cursor cursor = contentResolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, projection, selection, new String[] {playlistUrl}, null)) {
                            if (cursor != null && cursor.moveToFirst()) {
                                playlistId = cursor.getLong(0);
                            }
                        }
                        ArrayList<String> playlistFileIds = new ArrayList<>();
                        try (InputStream stream = openReadStream(filePath)) {
                        try (InputStreamReader streamReader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        try (BufferedReader reader = new BufferedReader(streamReader)) {
                            while (true) {
                                playlistFileUrl = reader.readLine();
                                if (playlistFileUrl == null) {
                                    break;
                                }
                                playlistFileUrl = playlistFileUrl.replace('\\', '/');
                                if (playlistFileUrl.startsWith("/")) {
                                    playlistFileUrl = storageRootPath + playlistFileUrl;
                                } else {
                                    playlistFileUrl = playlistFolderPath.resolve(playlistFileUrl).toString();
                                }
                                String fileId = fileIdLookup.get(playlistFileUrl);
                                if (fileId != null) {
                                    playlistFileIds.add(fileId);
                                }
                            }
                        }}}
                        ContentValues[] playlistFileUrlValues = new ContentValues[playlistFileIds.size()];
                        for (int index = 0; index < playlistFileIds.size(); index ++) {
                            values = new ContentValues();
                            values.put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, (long) index);
                            values.put(MediaStore.Audio.Playlists.Members.AUDIO_ID, Long.parseLong(playlistFileIds.get(index)));
                            playlistFileUrlValues[index] = values;
                        }
                        Uri playlistUri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
                        contentResolver.delete(playlistUri, null, null);
                        contentResolver.bulkInsert(playlistUri, playlistFileUrlValues);
                    } catch (Exception ex) {
                        ErrorHandler.logError("scanPlaylists", ex, "playlist=" + ((playlistUrl == null) ? "null" : playlistUrl + ",path=" + ((playlistFolderPath == null) ? "null" : playlistFolderPath.toString())) + "file=" + ((playlistFileUrl == null) ? "null" : playlistFileUrl));
                    }
                }
            } catch (Exception ex) {
                ErrorHandler.logError("updatePlaylists", ex);
            }
        }
        if (WifiSyncServiceSettings.debugMode) {
            ErrorHandler.logInfo("waitScanFiles", "done");
        }
    }

    static File getSdCardFromIndex(Context context, int index) {
        try {
            File[] storageRootPaths = context.getExternalFilesDirs(null);
            for (File root : storageRootPaths) {
                String path = root.getPath();
                int charIndex = path.indexOf("/Android/");
                if (charIndex != -1) {
                    if (index > 1) {
                        index -= 1;
                    } else {
                        path = path.substring(0, charIndex);
                        return new File(path);
                    }
                }
            }
        } catch (Exception ex) {
            ErrorHandler.logError("getSdCards", ex);
        }
        return null;
    }
}

class SyncResultsInfo {
    final static short DIRECTION_REVERSE_SYNC = -1;
    final static short DIRECTION_NONE = 0;
    final static short DIRECTION_FORWARD_SYNC = 1;
    final static short ALERT_INFO = 0;
    final static short ALERT_WARNING = 1;
    final static short ALERT_SEVERE = 2;
    final short direction;
    final String action;
    final String targetName;
    final String estimatedSize;
    final short alert;
    final String message;

    SyncResultsInfo(String message) {
        direction = DIRECTION_NONE;
        this.action = null;
        this.targetName = null;
        this.estimatedSize = null;
        this.alert = ALERT_INFO;
        this.message = message;
    }

    SyncResultsInfo(String action, String targetName, String estimateSize) {
        direction = DIRECTION_FORWARD_SYNC;
        this.action = action;
        this.targetName = targetName;
        this.estimatedSize = estimateSize;
        this.alert = ALERT_INFO;
        this.message = null;
    }

    SyncResultsInfo(String targetName, String message) {
        this.direction = DIRECTION_FORWARD_SYNC;
        this.action = null;
        this.targetName = targetName;
        this.estimatedSize = null;
        this.alert = ALERT_SEVERE;
        this.message = message;
    }

    SyncResultsInfo(String action, String targetName, short alert, String message) {
        direction = DIRECTION_REVERSE_SYNC;
        this.action = action;
        this.targetName = targetName;
        this.estimatedSize = null;
        this.alert = alert;
        this.message = message;
    }
}

class FileInfo {
    final String filename;
    final long length;
    final long dateModified;

    FileInfo(String filename, long dateModified) {
        this.filename = filename;
        this.length = 0;
        this.dateModified = dateModified;
    }

    FileInfo(String filename, long length, long dateModified) {
        this.filename = filename;
        this.length = length;
        this.dateModified = dateModified;
    }
}

class FileErrorInfo {
    static final int ERROR_COPY = 0;
    static final int ERROR_DELETE = 1;
    final int errorCategory;
    final String filename;
    final String errorMessage;

    FileErrorInfo(int errorCategory, String filename, String errorMessage) {
        this.errorCategory = errorCategory;
        this.filename = filename;
        this.errorMessage = errorMessage;
    }
}

class WifiSyncServiceSettings {
    static String defaultIpAddressValue = "";
    static String deviceName = Build.MODEL;
    static int deviceStorageIndex = 0;
    static final Map<String, String> permissionPathToSdCardMapping = new HashMap<>();
    static final AtomicReference<Uri> accessPermissionsUri = new AtomicReference<>();
    static boolean syncFromMusicBee = true;
    static boolean syncCustomFiles = false;
    static boolean syncDeleteUnselectedFiles = false;
    static final ArrayList<String> syncCustomPlaylistNames = new ArrayList<>();
    static final int PLAYER_GONEMAD = 1;
    static final int PLAYER_POWERAMP = 2;
    static int reverseSyncPlayer = 0;
    static boolean reverseSyncPlaylists = false;
    static String reverseSyncPlaylistsPath = "";
    static boolean reverseSyncRatings = true;
    static boolean reverseSyncPlayCounts = true;
    static boolean debugMode = false;

    static void loadSettings(final Context context) {
        defaultIpAddressValue = "";
        try {
            File settingsFile = new File(context.getFilesDir(), "MusicBeeWifiSyncSettings.dat");
            if (settingsFile.exists()) {
                try (FileInputStream fs = new FileInputStream(settingsFile)) {
                try (DataInputStream reader = new DataInputStream(fs)) {
                    int version = reader.readInt();
                    defaultIpAddressValue = reader.readUTF();
                    deviceName = reader.readUTF();
                    deviceStorageIndex = reader.readInt();
                    syncFromMusicBee = reader.readBoolean();
                    if (version < 5) {
                        syncFromMusicBee = true;
                    }
                    if (version > 1) {
                        int count = reader.readInt();
                        while (count > 0) {
                            count --;
                            String permissionsPath = reader.readUTF();
                            String sdCardPath = reader.readUTF();
                            permissionPathToSdCardMapping.put(permissionsPath, sdCardPath);
                        }
                        if (version > 2) {
                            syncDeleteUnselectedFiles = reader.readBoolean();
                            count = reader.readInt();
                            while (count > 0) {
                                count --;
                                syncCustomPlaylistNames.add(reader.readUTF());
                            }
                            if (version > 3) {
                                reverseSyncPlayer = reader.readInt();
                                reverseSyncPlaylists = reader.readBoolean();
                                reverseSyncRatings = reader.readBoolean();
                                reverseSyncPlayCounts = reader.readBoolean();
                                reverseSyncPlaylistsPath = reader.readUTF();
                            }
                        }
                    }
                }}
            }
        }
        catch (Exception ex) {
            ErrorHandler.logError("loadSettings", ex);
        }
    }

    static void saveSettings(final Context context) {
        try {
            File settingsFile = new File(context.getFilesDir(), "MusicBeeWifiSyncSettings.dat");
            try (FileOutputStream fs = new FileOutputStream(settingsFile)) {
            try (DataOutputStream writer = new DataOutputStream(fs)) {
                writer.writeInt(5);
                writer.writeUTF(defaultIpAddressValue);
                writer.writeUTF(deviceName);
                writer.writeInt(deviceStorageIndex);
                writer.writeBoolean(syncFromMusicBee);
                writer.writeInt(permissionPathToSdCardMapping.size());
                for (Map.Entry<String, String> item : permissionPathToSdCardMapping.entrySet()) {
                    writer.writeUTF(item.getKey());
                    writer.writeUTF(item.getValue());
                }
                writer.writeBoolean(syncDeleteUnselectedFiles);
                writer.writeInt(syncCustomPlaylistNames.size());
                for (String playlistName : syncCustomPlaylistNames) {
                    writer.writeUTF(playlistName);
                }
                writer.writeInt(reverseSyncPlayer);
                writer.writeBoolean(reverseSyncPlaylists);
                writer.writeBoolean(reverseSyncRatings);
                writer.writeBoolean(reverseSyncPlayCounts);
                writer.writeUTF(reverseSyncPlaylistsPath);
            }}
        }
        catch (Exception ex) {
            ErrorHandler.logError("saveSettings", ex);
        }
    }
}
