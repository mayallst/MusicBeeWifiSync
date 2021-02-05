package com.getmusicbee.musicbeewifisync;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Build;
import androidx.core.view.MenuCompat;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;

public class MainActivity extends WifiSyncBaseActivity {
    private boolean syncPreview;
    private CheckBox syncToPlaylists;
    private EditText syncToPlaylistsPath;
    private Button syncPreviewButton;
    private LinearLayout syncStartButton;
    private Thread serverStatusThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ErrorHandler.initialise(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // needed so android "Recent Views" actually shows the icon - only seems to be an issue with P
            setTaskDescription(new ActivityManager.TaskDescription(null, R.drawable.ic_launcher_round));
        }
        WifiSyncServiceSettings.loadSettings(this);
        if (WifiSyncServiceSettings.defaultIpAddressValue.length() == 0) {
            Intent intent = new Intent(this, SettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else if (WifiSyncService.syncIsRunning.get()) {
            Intent intent;
            intent = new Intent(this, SyncResultsStatusActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            setContentView(R.layout.activity_main);
            PermissionsHandler.demandInternalStorageAccessPermissions(this);
            syncPreviewButton = findViewById(R.id.syncPreviewButton);
            syncStartButton = findViewById(R.id.syncStartButton);
            syncToPlaylists = findViewById(R.id.syncToPlaylists);
            syncToPlaylistsPath = findViewById(R.id.syncToPlaylistPath);
            final CheckBox syncFromMusicBee = findViewById(R.id.syncFromMusicBee);
            syncFromMusicBee.setChecked(WifiSyncServiceSettings.syncFromMusicBee);
            syncFromMusicBee.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    WifiSyncServiceSettings.syncFromMusicBee = syncFromMusicBee.isChecked();
                    WifiSyncServiceSettings.saveSettings(mainWindow);
                }
            });
            final CheckBox syncToRatings = findViewById(R.id.syncToRatings);
            final CheckBox syncToPlayCounts = findViewById(R.id.syncToPlayCounts);
            final RadioGroup syncToUsingPlayer = findViewById(R.id.syncToUsingPlayer);
            boolean playlistsSupported = false;
            switch (WifiSyncServiceSettings.reverseSyncPlayer) {
                case WifiSyncServiceSettings.PLAYER_GONEMAD:
                    playlistsSupported = true;
                    syncToUsingPlayer.check(R.id.syncPlayerGoneMad);
                    break;
                case WifiSyncServiceSettings.PLAYER_POWERAMP:
                    syncToUsingPlayer.check(R.id.syncPlayerPowerAmp);
                    break;
            }
            syncToUsingPlayer.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
                public void onCheckedChanged(RadioGroup group, int checkedId) {
                    switch (syncToUsingPlayer.getCheckedRadioButtonId()) {
                        case R.id.syncPlayerGoneMad:
                            WifiSyncServiceSettings.reverseSyncPlaylistsPath = "/gmmp/playlists";
                            WifiSyncServiceSettings.reverseSyncPlayer = WifiSyncServiceSettings.PLAYER_GONEMAD;
                            setPlaylistsEnabled(true);
                            break;
                        case R.id.syncPlayerPowerAmp:
                            WifiSyncServiceSettings.reverseSyncPlaylistsPath = "";
                            WifiSyncServiceSettings.reverseSyncPlayer = WifiSyncServiceSettings.PLAYER_POWERAMP;
                            setPlaylistsEnabled(false);
                            break;
                    }
                    WifiSyncServiceSettings.saveSettings(mainWindow);
                }
            });
            setPlaylistsEnabled(playlistsSupported);
            syncToPlaylists.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    WifiSyncServiceSettings.reverseSyncPlaylists = syncToPlaylists.isChecked();
                    WifiSyncServiceSettings.saveSettings(mainWindow);
                }
            });
            syncToPlaylistsPath.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    WifiSyncServiceSettings.reverseSyncPlaylistsPath = syncToPlaylistsPath.getText().toString();
                    WifiSyncServiceSettings.saveSettings(mainWindow);
                    return false;
                }
            });
            syncToRatings.setChecked(WifiSyncServiceSettings.reverseSyncRatings);
            syncToRatings.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    WifiSyncServiceSettings.reverseSyncRatings = syncToRatings.isChecked();
                    WifiSyncServiceSettings.saveSettings(mainWindow);
                }
            });
            syncToPlayCounts.setChecked(WifiSyncServiceSettings.reverseSyncPlayCounts);
            syncToPlayCounts.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    WifiSyncServiceSettings.reverseSyncPlayCounts = syncToPlayCounts.isChecked();
                    WifiSyncServiceSettings.saveSettings(mainWindow);
                }
            });
            checkServerStatus();
        }
    }

    private void setPlaylistsEnabled(boolean enabled) {
        if (!enabled) {
            WifiSyncServiceSettings.reverseSyncPlaylists = false;
        }
        syncToPlaylists.setEnabled(enabled);
        syncToPlaylists.setChecked(WifiSyncServiceSettings.reverseSyncPlaylists);
        syncToPlaylistsPath.setEnabled(enabled);
        syncToPlaylistsPath.setText(WifiSyncServiceSettings.reverseSyncPlaylistsPath);
    }

    @Override
    protected void onDestroy() {
        if (serverStatusThread != null) {
            serverStatusThread.interrupt();
            serverStatusThread = null;
        }
        mainWindow = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        MenuItem fullSyncItem = menu.findItem(R.id.fullSyncMenuItem);
        fullSyncItem.setCheckable(true);
        fullSyncItem.setChecked(true);
        MenuItem playlistSyncMenuItem = menu.findItem(R.id.playlistSyncMenuItem);
        playlistSyncMenuItem.setCheckable(false);
        playlistSyncMenuItem.setChecked(false);
        return true;
    }

    public void onSyncPreviewButton_Click(View view) {
        if (isConfigOK()) {
            syncPreviewButton.setEnabled(false);
            try {
                WifiSyncServiceSettings.syncCustomFiles = false;
                syncPreview = true;
                if (tryGetStorageAccessGrant()) {
                    WifiSyncService.startSynchronisation(this, 0, true, false);
                }
            } finally {
                syncPreviewButton.setEnabled(true);
            }
        }
    }

    public void onSyncStartButton_Click(View view) {
        if (isConfigOK()) {
            syncStartButton.setEnabled(false);
            try {
                WifiSyncServiceSettings.syncCustomFiles = false;
                syncPreview = false;
                if (tryGetStorageAccessGrant()) {
                    WifiSyncService.startSynchronisation(this, 0, false, false);
                }
            } finally {
                syncStartButton.setEnabled(true);
            }
        }
    }

    private boolean isConfigOK() {
        String message = null;
        if (serverStatusThread != null) {
            message = getString(R.string.errorServerNotFound);
        }
        boolean anyReverseSync = (WifiSyncServiceSettings.reverseSyncPlaylists || WifiSyncServiceSettings.reverseSyncRatings || WifiSyncServiceSettings.reverseSyncPlayCounts);
        if (!anyReverseSync) {
            if (!WifiSyncServiceSettings.syncFromMusicBee) {
                message = getString(R.string.errorSyncParamsNoneSelected);
            }
        } else {
            if (WifiSyncServiceSettings.reverseSyncPlayer == 0) {
                message = getString(R.string.errorSyncParamsPlayerNotSelected);
            }
        }
        if (message == null) {
            return true;
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(mainWindow);
            builder.setTitle(getString(R.string.syncErrorHeader));
            builder.setMessage(message);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setCancelable(false);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
            return false;
        }
    }

    @Override
    protected void onStoragePermissionsApproved() {
        WifiSyncService.startSynchronisation(this, 0, syncPreview, false);
    }

    private void checkServerStatus() {
        serverStatusThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean statusDisplayed = false;
                    while (true) {
                        if (WifiSyncService.ServerPinger.ping()) {
                            if (statusDisplayed) {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (serverStatusThread != null) {
                                            serverStatusThread = null;
                                            findViewById(R.id.syncServerStatus).setVisibility(View.GONE);
                                        }
                                    }
                                });
                            }
                            break;
                        }
                        if (!statusDisplayed) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    findViewById(R.id.syncServerStatus).setVisibility(View.VISIBLE);
                                }
                            });
                            statusDisplayed = true;
                        }
                        Thread.sleep(2500);
                    }
                } catch (Exception ex) {
                }
                serverStatusThread = null;
            }
        });
        serverStatusThread.start();
    }
}
