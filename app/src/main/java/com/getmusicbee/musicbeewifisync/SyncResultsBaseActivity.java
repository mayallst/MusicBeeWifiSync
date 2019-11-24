package com.getmusicbee.musicbeewifisync;

import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import android.support.annotation.NonNull;

public abstract class SyncResultsBaseActivity extends AppCompatActivity {
    protected SyncResultsBaseActivity mainWindow = this;
    protected int infoColor;
    protected int errorColor;
    protected int warningColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        infoColor = ContextCompat.getColor(this, R.color.colorButtonTextDisabled);
        errorColor = ContextCompat.getColor(this, R.color.colorWarning);
        warningColor = ContextCompat.getColor(this, R.color.colorWarning);
    }

    @Override
    protected void onDestroy() {
        mainWindow = null;
        WifiSyncService.syncFromResults = null;
        WifiSyncService.syncToResults = null;
        super.onDestroy();
    }

    protected void showResults(final ListView resultsListView, ArrayList<SyncResultsInfo> resultsToData, ArrayList<SyncResultsInfo> resultsFromData) {
        final int maxResults = 256;
        if (resultsToData == null) {
            resultsToData = new ArrayList<>();
        }
        if (resultsFromData == null) {
            resultsFromData = new ArrayList<>();
        }
        int resultsToDataCount = resultsToData.size();
        int resultsFromDataCount = resultsFromData.size();
        ArrayList<SyncResultsInfo> filteredPreviewData;
        if (resultsToDataCount + resultsFromDataCount < maxResults + 16) {
            filteredPreviewData = new ArrayList<>(resultsToDataCount + resultsFromDataCount);
            filteredPreviewData.addAll(resultsToData);
            filteredPreviewData.addAll(resultsFromData);
        } else {
            filteredPreviewData = new ArrayList<>(maxResults + 2);
            int filteredPreviewFromCount = resultsFromDataCount;
            int filteredPreviewToCount = resultsToDataCount;
            if (resultsToDataCount < maxResults / 4) {
                filteredPreviewFromCount = maxResults - resultsToDataCount;
            } else if (resultsFromDataCount < maxResults/ 4) {
                filteredPreviewToCount = maxResults - resultsFromDataCount;
            } else {
                double scaling = (double) maxResults / (double) (resultsToDataCount + resultsFromDataCount);
                filteredPreviewToCount *= scaling;
                filteredPreviewFromCount *= scaling;
            }
            for (int index = 0; index < filteredPreviewToCount; index ++) {
                filteredPreviewData.add(resultsToData.get(index));
            }
            if (filteredPreviewToCount < resultsToDataCount) {
                filteredPreviewData.add(new SyncResultsInfo(String.format(getString(R.string.syncPreviewMoreResults), (resultsToDataCount - filteredPreviewToCount))));
            }
            for (int index = 0; index < filteredPreviewFromCount; index ++) {
                filteredPreviewData.add(resultsFromData.get(index));
            }
            if (filteredPreviewFromCount < resultsFromDataCount) {
                filteredPreviewData.add(new SyncResultsInfo(String.format(getString(R.string.syncPreviewMoreResults), (resultsFromDataCount - filteredPreviewFromCount))));
            }
        }
        final ArrayList<SyncResultsInfo> resultsData = filteredPreviewData;
        final ArrayAdapter<SyncResultsInfo> adapter = new ArrayAdapter<SyncResultsInfo>(mainWindow, R.layout.row_item_sync_results, R.id.syncResultsLine1, resultsData) {
            @Override
            public @NonNull View getView(int position, View convertView, @NonNull ViewGroup parent) {
                final View view = super.getView(position, convertView, parent);
                final ImageView syncResultsDirectionIcon = view.findViewById(R.id.syncResultsDirectionIcon);
                final ImageView syncResultsStatusIcon = view.findViewById(R.id.syncResultsStatusIcon);
                final TextView syncResultsLine1 = view.findViewById(R.id.syncResultsLine1);
                final TextView syncResultsLine2 = view.findViewById(R.id.syncResultsLine2);
                final SyncResultsInfo info = resultsData.get(position);
                if (info.direction == SyncResultsInfo.DIRECTION_NONE) {
                    // more results message
                    syncResultsStatusIcon.setVisibility(View.GONE);
                    syncResultsDirectionIcon.setVisibility(View.GONE);
                    syncResultsLine1.setVisibility(View.GONE);
                    syncResultsLine2.setText(info.message);
                } else if (info.direction == SyncResultsInfo.DIRECTION_REVERSE_SYNC || info.alert != SyncResultsInfo.ALERT_INFO) {
                    // reverse sync action
                    syncResultsDirectionIcon.setVisibility(View.VISIBLE);
                    syncResultsDirectionIcon.setImageResource((info.direction == SyncResultsInfo.DIRECTION_REVERSE_SYNC) ? R.drawable.ic_arrow_back : R.drawable.ic_arrow_forward);
                    int color;
                    if (info.alert == SyncResultsInfo.ALERT_INFO) {
                        syncResultsStatusIcon.setVisibility(View.GONE);
                        color = infoColor;
                    } else {
                        color = (info.alert == SyncResultsInfo.ALERT_WARNING) ? warningColor : errorColor;
                        syncResultsStatusIcon.setVisibility(View.VISIBLE);
                        syncResultsStatusIcon.setImageResource(android.R.drawable.stat_notify_error);
                        syncResultsStatusIcon.setColorFilter(color);
                    }
                    syncResultsDirectionIcon.setColorFilter(infoColor);
                    syncResultsLine1.setTextColor(color);
                    syncResultsLine1.setText(info.targetName);
                    syncResultsLine2.setText(info.message);
                } else {
                    // sync action
                    syncResultsDirectionIcon.setVisibility(View.VISIBLE);
                    syncResultsDirectionIcon.setImageResource(R.drawable.ic_arrow_forward);
                    syncResultsDirectionIcon.setColorFilter(infoColor);
                    syncResultsStatusIcon.setVisibility(View.GONE);
                    syncResultsLine1.setTextColor(infoColor);
                    syncResultsLine1.setText(info.targetName);
                    syncResultsLine2.setText((info.estimatedSize.length() == 0) ? info.action : info.action + " - " + info.estimatedSize);
                }
                return view;
            }
        };
        resultsListView.setAdapter(adapter);
    }
}
