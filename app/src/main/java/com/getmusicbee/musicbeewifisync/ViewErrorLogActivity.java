package com.getmusicbee.musicbeewifisync;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

public class ViewErrorLogActivity extends AppCompatActivity {
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_error_log);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        errorText = findViewById(R.id.errorText);
        errorText.setMovementMethod(new ScrollingMovementMethod());
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                final String errorLog = ErrorHandler.getLog();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (errorLog == null || errorLog.length() == 0) {
                            errorText.setText(R.string.errorNone);
                            findViewById(R.id.copyToClipboardButton).setEnabled(false);
                        } else {
                            errorText.setText(errorLog);
                        }
                    }
                });
            }
        });
        thread.start();
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    public void copyToClipboardButton_Click(View view) {
        try {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.title_activity_view_error_log), errorText.getText().toString()));
        } catch (Exception ex) {
        }
    }
}
