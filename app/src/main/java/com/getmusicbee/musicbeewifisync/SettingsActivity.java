package com.getmusicbee.musicbeewifisync;

import android.content.Intent;
import android.os.Build;

import androidx.core.view.MenuCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class SettingsActivity extends WifiSyncBaseActivity {
    private boolean initialSetup;
    private Button locateServerButton;
    private ProgressBar settingsWaitIndicator;
    private TextView settingsLocateServerNoConfig;
    private RadioGroup settingsStorageOptions;
    private RadioButton settingsStorageSdCard1;
    private RadioButton settingsStorageSdCard2;
    private Button settingsGrantAccessButton;
    private CheckBox settingsDebugMode;
    private TextView settingsDeviceNamePrompt;
    private EditText settingsDeviceName;
    private EditText settingsServerIpOverride;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        PermissionsHandler.demandInternalStorageAccessPermissions(this);
        initialSetup = (WifiSyncServiceSettings.defaultIpAddressValue.length() == 0);
        locateServerButton = findViewById(R.id.locateServerButton);
        settingsServerIpOverride = findViewById(R.id.settingsServerIpOverride);
        settingsWaitIndicator = findViewById(R.id.settingsWaitIndicator);
        settingsLocateServerNoConfig = findViewById(R.id.settingsLocateServerNoConfig);
        TextView settingsStoragePrompt = findViewById(R.id.settingsStoragePrompt);
        settingsStorageOptions = findViewById(R.id.settingsStorageOptions);
        settingsStorageOptions.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                showGrantAccessButton();
            }
        });
        RadioButton settingsStorageInternal = findViewById(R.id.settingsStorageInternal);
        settingsStorageSdCard1 = findViewById(R.id.settingsStorageSdCard1);
        settingsStorageSdCard2 = findViewById(R.id.settingsStorageSdCard2);
        settingsGrantAccessButton = findViewById(R.id.settingsGrantAccessButton);
        settingsDebugMode = findViewById(R.id.settingsDebugMode);
        settingsDebugMode.setChecked(WifiSyncServiceSettings.debugMode);
        settingsDebugMode.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                WifiSyncServiceSettings.debugMode = settingsDebugMode.isChecked();
            }
        });
        settingsDeviceNamePrompt = findViewById(R.id.settingsDeviceNamePrompt);
        settingsDeviceName = findViewById(R.id.settingsDeviceName);
        int externalSdCardCount = this.getExternalFilesDirs(null).length - 1;
        if (externalSdCardCount == 0) {
            settingsStorageInternal.setChecked(true);
            settingsStorageSdCard1.setChecked(false);
            settingsStorageSdCard1.setEnabled(false);
            settingsStorageSdCard2.setVisibility(View.GONE);
        } else {
            settingsStorageInternal.setChecked((WifiSyncServiceSettings.deviceStorageIndex == 1));
            settingsStorageSdCard1.setChecked((WifiSyncServiceSettings.deviceStorageIndex <= 0 || WifiSyncServiceSettings.deviceStorageIndex == 2 || WifiSyncServiceSettings.deviceStorageIndex > 1 + externalSdCardCount));
            settingsStorageSdCard1.setEnabled(true);
            if (externalSdCardCount == 1) {
                if (WifiSyncServiceSettings.deviceStorageIndex <= 2) {
                    settingsStorageSdCard2.setVisibility(View.GONE);
                } else {
                    settingsStorageSdCard1.setText(R.string.settingsStorageSdCard1);
                    settingsStorageSdCard2.setText(R.string.settingsStorageSdCard2);
                    settingsStorageSdCard2.setEnabled(false);
                    settingsStorageSdCard2.setVisibility(View.VISIBLE);
                }
            } else {
                settingsStorageSdCard1.setText(R.string.settingsStorageSdCard1);
                settingsStorageSdCard2.setText(R.string.settingsStorageSdCard2);
                settingsStorageSdCard2.setVisibility(View.VISIBLE);
                settingsStorageSdCard2.setChecked(WifiSyncServiceSettings.deviceStorageIndex == 3);
            }
        }
        if (initialSetup) {
            WifiSyncServiceSettings.debugMode = true;
            settingsDebugMode.setVisibility(View.GONE);
        } else {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.setDisplayHomeAsUpEnabled(true);
                actionBar.setTitle(R.string.title_activity_settings2);
            }
            findViewById(R.id.settingsInfo0).setVisibility(View.GONE);
            findViewById(R.id.settingsInfo1).setVisibility(View.GONE);
            findViewById(R.id.settingsInfo2).setVisibility(View.GONE);
            locateServerButton.setVisibility(View.GONE);
            setServerIpTextBoxVisibility(View.GONE);
            settingsDebugMode.setVisibility(View.VISIBLE);
            settingsStoragePrompt.setText(R.string.settingsStorageSettingsPrompt);
            if (!Build.MODEL.equalsIgnoreCase(WifiSyncServiceSettings.deviceName)) {
                showNoConfigMatchedSettings();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (initialSetup) {
            WifiSyncServiceSettings.debugMode = false;
        } else {
            WifiSyncServiceSettings.deviceStorageIndex = (settingsStorageSdCard1.isChecked()) ? 2 : (settingsStorageSdCard2.isChecked()) ? 3 : 1;
            WifiSyncServiceSettings.saveSettings(this);
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    @Override
    protected void onStoragePermissionsApproved() {
        WifiSyncServiceSettings.saveSettings(this);
        showGrantAccessButton();
    }

    private void showGrantAccessButton() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            if (PermissionsHandler.isStorageAccessGranted(this, getCheckedStorageTypeButton())) {
                locateServerButton.setEnabled(true);
                locateServerButton.setTextColor(buttonTextEnabledColor);
            } else {
                locateServerButton.setEnabled(false);
                locateServerButton.setTextColor(buttonTextDisabledColor);
            }
            settingsGrantAccessButton.setVisibility(View.VISIBLE);
        }
    }

    public void onSyncGrantAccessButton_Click(View view) {
        grantAccessToSdCard = FileStorageAccess.getSdCardFromIndex(this, getCheckedStorageTypeButton());
        PermissionsHandler.demandStorageAccessPermissions(this, grantAccessToSdCard, new PermissionsHandler.Callback() {
            @Override
            public void processAccessRequestIntent(Intent accessRequestIntent) {
                startActivityForResult(accessRequestIntent, PermissionsHandler.RW_ACCESS_REQUEST_ONLY);
            }
        });
    }

    private int getCheckedStorageTypeButton() {
        switch (settingsStorageOptions.getCheckedRadioButtonId()) {
            case R.id.settingsStorageSdCard1:
                return 2;
            case R.id.settingsStorageSdCard2:
                return 3;
        }
        return 1;
    }

    public void onLocateServerButton_Click(View view) {
        settingsLocateServerNoConfig.setVisibility(View.GONE);
        WifiSyncServiceSettings.deviceStorageIndex = (settingsStorageSdCard1.isChecked()) ? 2 : (settingsStorageSdCard2.isChecked()) ? 3 : 1;
        WifiSyncServiceSettings.saveSettings(mainWindow);
        settingsWaitIndicator.setVisibility(View.VISIBLE);
        locateServerButton.setEnabled(false);
        locateServerButton.setTextColor(buttonTextDisabledColor);

        Thread locateServerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                InetAddress serverIP = null;
                if (!TextUtils.isEmpty(settingsServerIpOverride.getText().toString())) {
                    try {
                        serverIP = InetAddress.getByName(settingsServerIpOverride.getText().toString());
                    } catch (UnknownHostException e) {
                        runOnUiThread(new Runnable() {
                                          @Override
                                          public void run() {
                                              settingsServerIpOverride.setError(getText(R.string.errorInvalidIp));
                                              settingsWaitIndicator.setVisibility(View.INVISIBLE);
                                              locateServerButton.setEnabled(true);
                                              locateServerButton.setTextColor(buttonTextEnabledColor);
                                          }
                                      }
                        );
                        return;
                    }
                }
                final String serverIPAddress = WifiSyncService.getMusicBeeServerAddress(mainWindow, serverIP);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mainWindow != null) {
                            settingsWaitIndicator.setVisibility(View.INVISIBLE);
                            locateServerButton.setEnabled(true);
                            locateServerButton.setTextColor(buttonTextEnabledColor);
                            if (serverIPAddress == null) {
                                AlertDialog.Builder errorDialog = new AlertDialog.Builder(mainWindow);
                                errorDialog.setMessage(getText(R.string.errorServerNotFound));
                                errorDialog.setPositiveButton(android.R.string.ok, null);
                                errorDialog.show();
                            } else if (serverIPAddress.equals(getString(R.string.syncStatusFAIL))) {
                                settingsLocateServerNoConfig.setVisibility(View.VISIBLE);
                                showNoConfigMatchedSettings();
                                settingsDeviceNamePrompt.setVisibility(View.GONE);
                            } else {
                                WifiSyncServiceSettings.defaultIpAddressValue = serverIPAddress;
                                WifiSyncServiceSettings.saveSettings(mainWindow);
                                Intent intent = new Intent(mainWindow, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                            }
                        }
                    }
                });
            }
        });
        locateServerThread.start();
    }

    private void setServerIpTextBoxVisibility(int visibility) {
        settingsServerIpOverride.setVisibility(visibility);
        findViewById(R.id.settingsServerIpLabel).setVisibility(visibility);
        findViewById(R.id.settingsServerIpPrompt).setVisibility(visibility);
    }

    private void showNoConfigMatchedSettings() {
        settingsDeviceNamePrompt.setVisibility(View.VISIBLE);
        settingsDeviceName.setText(WifiSyncServiceSettings.deviceName);
        settingsDeviceName.setVisibility(View.VISIBLE);
        settingsDeviceName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                WifiSyncServiceSettings.deviceName = settingsDeviceName.getText().toString();
                WifiSyncServiceSettings.saveSettings(mainWindow);
                return false;
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_settings, menu);
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
        }
        return super.onOptionsItemSelected(item);
    }
}
