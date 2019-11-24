package com.getmusicbee.musicbeewifisync;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Handler;
import android.support.constraint.ConstraintLayout;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v4.view.MenuCompat;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;

public class SyncResultsStatusActivity extends SyncResultsBaseActivity {
    private ProgressBar syncProgressBar;
    private ProgressBar syncWaitIndicator;
    private TextView syncCompletionStatusMessage;
    private ListView syncFailedResults;
    private TextView syncProgressMessage;
    private Button stopSyncButton;
    private final Handler timerHandler = new Handler();
    private Runnable timerRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_status);
        WifiSyncService.resultsActivityReady.set();
        syncCompletionStatusMessage = findViewById(R.id.syncCompletionStatusMessage);
        syncFailedResults = findViewById(R.id.syncFailedResults);
        syncProgressBar = findViewById(R.id.syncProgressBar);
        syncWaitIndicator = findViewById(R.id.syncWaitIndicator);
        syncProgressMessage = findViewById(R.id.syncProgressMessage);
        stopSyncButton = findViewById(R.id.stopSyncButton);
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    if (WifiSyncService.syncPercentCompleted.get() == -1) {
                        showEndOfSyncInformation();
                    } else {
                        syncProgressMessage.setText(WifiSyncService.syncProgressMessage.get());
                        syncProgressBar.setProgress(WifiSyncService.syncPercentCompleted.get());
                        timerHandler.postDelayed(this, 300);
                    }
                } catch (Exception ex) {
                    ErrorHandler.logError("startProgress", ex);
                }
            }
        };
        timerHandler.postDelayed(timerRunnable, 300);
    }

    @Override
    protected void onDestroy() {
        WifiSyncService.resultsActivityReady.reset();
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        mainWindow = null;
        super.onDestroy();
    }

    @Override
    public void onBackPressed(){
        // disable back button
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
            case R.id.wifiSyncLogMenuItem:
                intent = new Intent(this, ViewErrorLogActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            case R.id.wifiSyncSettingsMenuItem:
                intent = new Intent(this, SettingsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onStopSyncButton_Click(View view) {
        if (stopSyncButton.getText().equals(getString(R.string.syncMore))) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            Intent intent = new Intent();
            intent.setClass(this, WifiSyncService.class);
            intent.setAction(getString(R.string.actionSyncAbort));
            startService(intent);
            stopProgressTimer();
            WifiSyncServiceSettings.saveSettings(this);
            WifiSyncService.syncErrorMessageId.set(R.string.syncCancelled);
            showEndOfSyncInformation();
        }
    }

    private void stopProgressTimer() {
        syncProgressBar.setVisibility(View.INVISIBLE);
        syncWaitIndicator.setVisibility(View.INVISIBLE);
        syncProgressMessage.setVisibility(View.GONE);
        if (timerHandler != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        stopSyncButton.setText(getString(R.string.syncMore));
    }

    private void showEndOfSyncInformation() {
        final int errorMessageId = WifiSyncService.syncErrorMessageId.getAndSet(0);
        WifiSyncServiceSettings.saveSettings(this);
        stopProgressTimer();
        if (errorMessageId == 0 || errorMessageId == R.string.syncCompletedFail) {
            int messageId = (errorMessageId != 0) ? errorMessageId : R.string.syncCompleted;
            syncCompletionStatusMessage.setText(messageId);
            syncCompletionStatusMessage.setVisibility(View.VISIBLE);
            if (errorMessageId == R.string.syncCompletedFail) {
                if ((WifiSyncService.syncToResults == null || WifiSyncService.syncToResults.size() == 0) && WifiSyncService.syncFailedFiles.size() == 0) {
                    syncCompletionStatusMessage.setText(R.string.syncCompletedFailErrorLog);
                    syncFailedResults.setVisibility(View.VISIBLE);
                } else {
                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams)syncCompletionStatusMessage.getLayoutParams();
                    params.verticalBias = 0.0f;
                    syncCompletionStatusMessage.setLayoutParams(params);
                    syncCompletionStatusMessage.setText(R.string.syncCompletedFailMessage);
                    syncFailedResults.setVisibility(View.VISIBLE);
                    ArrayList<SyncResultsInfo> failedFrom = new ArrayList<>();
                    for (FileErrorInfo info : WifiSyncService.syncFailedFiles) {
                        failedFrom.add(new SyncResultsInfo(info.filename.substring(info.filename.lastIndexOf("/") + 1), info.errorMessage));
                    }
                    showResults(syncFailedResults, WifiSyncService.syncToResults, failedFrom);
                }
            }
            Snackbar snackbar = Snackbar.make(stopSyncButton, getString(messageId), Snackbar.LENGTH_LONG);
            try {
                View snackbarView = snackbar.getView();
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) snackbarView.getLayoutParams();
                params.setMargins(0, 0, 0, stopSyncButton.getHeight());
                snackbarView.setLayoutParams(params);
            } catch (Exception ex) {
            }
            snackbar.show();
        }
        else if (errorMessageId == R.string.syncCancelled) {
            syncCompletionStatusMessage.setText(errorMessageId);
            syncCompletionStatusMessage.setVisibility(View.VISIBLE);
        }
        else {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getString(R.string.syncErrorHeader));
            builder.setMessage(getString(errorMessageId));
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setCancelable(false);
            if (errorMessageId != R.string.errorServerNotFound) {
                builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        syncCompletionStatusMessage.setText(errorMessageId);
                        syncCompletionStatusMessage.setVisibility(View.VISIBLE);
                    }
                });
            } else {
                builder.setNegativeButton(R.string.syncCancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        syncCompletionStatusMessage.setText(errorMessageId);
                        syncCompletionStatusMessage.setVisibility(View.VISIBLE);
                    }
                });
                builder.setPositiveButton(R.string.syncRetry, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        WifiSyncService.startSynchronisation(getApplicationContext(), WifiSyncService.syncIteration, false, false);
                    }
                });
            }
            builder.show();
        }
    }
}
