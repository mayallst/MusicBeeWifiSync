package com.getmusicbee.musicbeewifisync;

import android.app.AlertDialog;
import android.support.v4.view.MenuCompat;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import android.widget.ListView;
import android.widget.TextView;
import android.support.annotation.NonNull;

public class PlaylistSyncActivity extends WifiSyncBaseActivity {
    private boolean syncPreview;
    private Thread playlistLoaderThread;
    private static volatile ArrayList<FileSelectedInfo> selectedPlaylists = null;
    private CheckBox syncPlaylistsDeleteFiles;
    private ListView syncPlaylistsSelector;
    private TextView syncNoPlaylistsMessage;
    private ArrayAdapter<FileSelectedInfo> syncPlaylistSelectorAdapter;
    private TextView syncPlaylistsCountMessage;
    private Button syncPlaylistsPreviewButton;
    private LinearLayout syncPlaylistsStartButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_sync);
        syncPlaylistsDeleteFiles = findViewById(R.id.syncPlaylistsDeleteFiles);
        syncPlaylistsSelector = findViewById(R.id.syncPlaylistsSelector);
        syncNoPlaylistsMessage = findViewById(R.id.syncNoPlaylistsMessage);
        syncPlaylistsCountMessage = findViewById(R.id.syncPlaylistsCountMessage);
        syncPlaylistsPreviewButton = findViewById(R.id.syncPlaylistsPreviewButton);
        syncPlaylistsStartButton = findViewById(R.id.syncPlaylistsStartButton);
        syncPlaylistsDeleteFiles.setChecked(WifiSyncServiceSettings.syncDeleteUnselectedFiles);
        if (selectedPlaylists == null) {
            loadPlaylists();
        } else {
            showPlaylists();
        }
        syncPlaylistsDeleteFiles.setChecked(WifiSyncServiceSettings.syncDeleteUnselectedFiles);
    }

    @Override
    protected void onDestroy() {
        if (playlistLoaderThread != null) {
            playlistLoaderThread.interrupt();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        MenuItem fullSyncItem = menu.findItem(R.id.fullSyncMenuItem);
        fullSyncItem.setCheckable(false);
        fullSyncItem.setChecked(false);
        MenuItem playlistSyncMenuItem = menu.findItem(R.id.playlistSyncMenuItem);
        playlistSyncMenuItem.setCheckable(true);
        playlistSyncMenuItem.setChecked(true);
        return true;
    }

    @Override
    protected void onStoragePermissionsApproved() {
        WifiSyncService.startSynchronisation(this, 0, syncPreview, false);
    }

    public void onSyncPlaylistsPreviewButton_Click(View view) {
        syncPlaylistsPreviewButton.setEnabled(false);
        try {
            if (setSyncParameters()) {
                syncPreview = true;
                if (tryGetStorageAccessGrant()) {
                    WifiSyncService.startSynchronisation(this, 0, true, false);
                }
            }
        } finally {
            syncPlaylistsPreviewButton.setEnabled(true);
        }
    }

    public void onSyncPlaylistsStartButton_Click(View view) {
        syncPlaylistsStartButton.setEnabled(false);
        try {
            if (setSyncParameters()) {
                syncPreview = false;
                if (tryGetStorageAccessGrant()) {
                    WifiSyncService.startSynchronisation(this, 0, false, false);
                }
            }
        } finally {
            syncPlaylistsStartButton.setEnabled(true);
        }
    }

    private boolean setSyncParameters() {
        WifiSyncServiceSettings.syncCustomFiles = true;
        WifiSyncServiceSettings.syncDeleteUnselectedFiles = syncPlaylistsDeleteFiles.isChecked();
        if (selectedPlaylists != null) {
            WifiSyncServiceSettings.syncCustomPlaylistNames.clear();
            for (FileSelectedInfo info : selectedPlaylists) {
                if (info.checked) {
                    WifiSyncServiceSettings.syncCustomPlaylistNames.add(info.filename);
                }
            }
        }
        if (WifiSyncServiceSettings.syncCustomPlaylistNames.size() > 0) {
            WifiSyncServiceSettings.saveSettings(this);
            return true;
        } else {
            AlertDialog.Builder builder = new AlertDialog.Builder(mainWindow);
            builder.setTitle(getString(R.string.syncErrorHeader));
            builder.setMessage(getString(R.string.errorNoPlaylistsSelected));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setPositiveButton(android.R.string.ok, null);
            builder.show();
            return false;
        }
    }

    private void loadPlaylists() {
        playlistLoaderThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (true) {
                        try {
                            ArrayList<FileSelectedInfo> values = new ArrayList<>();
                            CaseInsensitiveMap lookup = new CaseInsensitiveMap();
                            for (String playlistName : WifiSyncServiceSettings.syncCustomPlaylistNames) {
                                lookup.put(playlistName, null);
                            }
                            for (String playlistName : WifiSyncService.getMusicBeePlaylists()) {
                                values.add(new FileSelectedInfo(playlistName, lookup.containsKey(playlistName)));
                            }
                            selectedPlaylists = values;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!playlistLoaderThread.isInterrupted()) {
                                        showPlaylists();
                                    }
                                }
                            });
                            return;
                        } catch (InterruptedException ex) {
                            throw ex;
                        } catch (SocketTimeoutException ex) {
                            showPlaylistRetrievalError();
                            Thread.sleep(2500);
                        } catch (Exception ex) {
                            ErrorHandler.logError("loadPlaylists", ex);
                            showPlaylistRetrievalError();
                            return;
                        }
                    }
                } catch (Exception ex) {
                }
            }
        });
        playlistLoaderThread.start();
    }

    private void showPlaylists() {
        if (mainWindow != null) {
            try {
                syncNoPlaylistsMessage.setVisibility(View.GONE);
                showPlaylistsSelectedCount();
                syncPlaylistSelectorAdapter = new ArrayAdapter<FileSelectedInfo>(mainWindow, R.layout.row_item_sync_playlist_selector, R.id.syncFileSelectorName, selectedPlaylists) {
                    @Override
                    public @NonNull
                    View getView(int position, View convertView, @NonNull ViewGroup parent) {
                        final View view = super.getView(position, convertView, parent);
                        final FileSelectedInfo info = selectedPlaylists.get(position);
                        final CheckBox filename = view.findViewById(R.id.syncFileSelectorName);
                        filename.setEnabled(syncPlaylistsSelector.isEnabled());
                        filename.setOnClickListener(new View.OnClickListener() {
                            public void onClick(View view) {
                                info.checked = !info.checked;
                                filename.setChecked(info.checked);
                                showPlaylistsSelectedCount();
                            }
                        });
                        filename.setText(info.filename);
                        filename.setChecked(info.checked);
                        return view;
                    }
                };
                syncPlaylistsSelector.setAdapter(syncPlaylistSelectorAdapter);
            } catch (Exception ex) {
                ErrorHandler.logError("showPlaylists", ex);
            }
        }
    }

    private void showPlaylistsSelectedCount() {
        int count = 0;
        if (selectedPlaylists != null) {
            for (FileSelectedInfo info : selectedPlaylists) {
                if (info.checked) {
                    count ++;
                }
            }
        }
        switch (count) {
            case 0:
                syncPlaylistsCountMessage.setText(R.string.syncPlaylists0);
                break;
            case 1:
                syncPlaylistsCountMessage.setText(R.string.syncPlaylists1);
                break;
            default:
                syncPlaylistsCountMessage.setText(String.format(getString(R.string.syncPlaylistsN), count));
        }
    }

    private void showPlaylistRetrievalError() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                syncNoPlaylistsMessage.setVisibility(View.VISIBLE);
            }
        });
    }

    private class FileSelectedInfo {
        final String filename;
        boolean checked;

        FileSelectedInfo(String filename, boolean checked) {
            this.filename = filename;
            this.checked = checked;
        }
    }
}
