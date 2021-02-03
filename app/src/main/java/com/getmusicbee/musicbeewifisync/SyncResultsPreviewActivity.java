package com.getmusicbee.musicbeewifisync;

import android.content.DialogInterface;
import android.content.Intent;
import androidx.core.view.MenuCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

public class SyncResultsPreviewActivity extends SyncResultsBaseActivity {
    private CheckBox syncExcludeErrors;
    private LinearLayout proceedSyncButton;
    private ImageView proceedSyncButtonImage;
    private TextView proceedSyncButtonText;
    private Thread waitResultsThread = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_preview);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        syncExcludeErrors = findViewById(R.id.syncExcludeErrors);
        proceedSyncButton = findViewById(R.id.proceedSyncButton);
        proceedSyncButtonImage = findViewById(R.id.proceedSyncButtonImage);
        proceedSyncButtonText = findViewById(R.id.proceedSyncButtonText);
        waitResultsThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    WifiSyncService.waitSyncResults.waitOne();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(R.id.previewWaitIndicator).setVisibility(View.GONE);
                            proceedSyncButton.setVisibility(View.VISIBLE);
                            final TextView previewStatusMessage = findViewById(R.id.previewStatusMessage);
                            final ListView previewListView = findViewById(R.id.previewResults);
                            final TextView previewErrorMessage = findViewById(R.id.previewErrorMessage);
                            ArrayList<SyncResultsInfo> previewToData = WifiSyncService.syncToResults;
                            ArrayList<SyncResultsInfo> previewFromData = WifiSyncService.syncFromResults;
                            if (mainWindow == null) {
                                // ignore
                            } else if (previewToData == null || previewFromData == null) {
                                disableProceedSyncButton();
                                int errorMessageId = WifiSyncService.syncErrorMessageId.get();
                                if (errorMessageId == 0) {
                                    errorMessageId = R.string.errorSyncNonSpecific;
                                }
                                previewStatusMessage.setText(errorMessageId);
                                AlertDialog.Builder builder = new AlertDialog.Builder(mainWindow);
                                builder.setTitle(getString(R.string.syncErrorHeader));
                                builder.setMessage(getString(errorMessageId));
                                builder.setIcon(android.R.drawable.ic_dialog_alert);
                                builder.setCancelable(false);
                                if (errorMessageId != R.string.errorServerNotFound) {
                                    builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            previewStatusMessage.setVisibility(View.VISIBLE);
                                        }
                                    });
                                } else {
                                    builder.setNegativeButton(R.string.syncCancel, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            previewStatusMessage.setVisibility(View.VISIBLE);
                                        }
                                    });
                                    builder.setPositiveButton(R.string.syncRetry, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            WifiSyncService.startSynchronisation(getApplicationContext(), 0, true, false);
                                            finish();
                                        }
                                    });
                                }
                                builder.show();

                            } else if (previewToData.size() == 0 && previewFromData.size() == 0) {
                                disableProceedSyncButton();
                                previewStatusMessage.setText(R.string.syncPreviewNoResults);
                                previewStatusMessage.setVisibility(View.VISIBLE);
                            } else {
                                final int previewToDataCount = previewToData.size();
                                final int previewFromDataCount = previewFromData.size();
                                int okCount = 0;
                                int warningCount = 0;
                                int failedCount = 0;
                                for (int index = 0; index < previewToData.size(); index ++) {
                                    switch (previewToData.get(index).alert) {
                                        case 0:
                                            okCount += 1;
                                            break;
                                        case 1:
                                            warningCount += 1;
                                            break;
                                        case 2:
                                        case 3:
                                            failedCount += 1;
                                            break;
                                    }
                                }
                                if (warningCount > 0) {
                                    previewErrorMessage.setTextColor(warningColor);
                                    previewErrorMessage.setText(String.format(getString(R.string.reverseSyncWarnings), (warningCount == 1) ? getString(R.string.reverseSyncFilesWarning1) : String.format(getString(R.string.reverseSyncFilesWarningN), warningCount)));
                                    previewErrorMessage.setVisibility(View.VISIBLE);
                                    syncExcludeErrors.setVisibility(View.VISIBLE);
                                } else if (failedCount > 0) {
                                    previewErrorMessage.setTextColor(errorColor);
                                    previewErrorMessage.setText(String.format(getString(R.string.reverseSyncFailed), (failedCount == 1) ? getString(R.string.reverseSyncFilesWarning1) : String.format(getString(R.string.reverseSyncFilesWarningN), failedCount)));
                                    previewErrorMessage.setVisibility(View.VISIBLE);
                                    syncExcludeErrors.setVisibility(View.INVISIBLE);
                                } else {
                                    previewErrorMessage.setVisibility(View.GONE);
                                    syncExcludeErrors.setVisibility(View.GONE);
                                }
                                if (previewToDataCount > 0 && previewFromDataCount == 0 && okCount == 0 && warningCount == 0) {
                                    disableProceedSyncButton();
                                }
                                previewListView.setVisibility(View.VISIBLE);
                                showResults(previewListView, previewToData, previewFromData);
                            }
                        }
                    });
                } catch (InterruptedException ex) {
                    // ignore
                } catch (Exception ex) {
                    ErrorHandler.logError("preview", ex);
                }
            }
        });
        waitResultsThread.start();
    }

    @Override
    protected void onDestroy() {
        if (waitResultsThread != null) {
            waitResultsThread.interrupt();
        }
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_sync_status, menu);
        MenuCompat.setGroupDividerEnabled(menu, true);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent intent;
        switch (item.getItemId()) {
            case R.id.wifiSyncSettingsMenuItem:
                intent = new Intent(this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            case R.id.wifiSyncLogMenuItem:
                intent = new Intent(this, ViewErrorLogActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp(){
        finish();
        return true;
    }

    private void disableProceedSyncButton() {
        proceedSyncButton.setEnabled(false);
        //DrawableCompat.setTint(proceedSyncButtonImage.getDrawable(), infoColor);
        proceedSyncButtonImage.setColorFilter(infoColor);
        proceedSyncButtonText.setTextColor(infoColor);
    }

    public void onProceedSyncButton_Click(View view) {
        WifiSyncService.startSynchronisation(this, 1, false, !syncExcludeErrors.isChecked());
        finish();
    }
}
