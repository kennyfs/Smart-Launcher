/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.allapps.search;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.AnyThread;

import com.android.launcher3.LauncherAppState;
import com.android.launcher3.allapps.AllAppsGridAdapter.AdapterItem;
import com.android.launcher3.model.AllAppsList;
import com.android.launcher3.model.BaseModelUpdateTask;
import com.android.launcher3.model.BgDataModel;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.search.SearchAlgorithm;
import com.android.launcher3.search.SearchCallback;
import com.android.launcher3.search.StringMatcherUtility;

import java.util.ArrayList;
import java.util.List;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm implements SearchAlgorithm<AdapterItem> {

    public static final int MAX_RESULTS_COUNT = 5;

    public final LauncherAppState mAppState;
    public final Handler mResultHandler;

    public DefaultAppSearchAlgorithm(Context context) {
        mAppState = LauncherAppState.getInstance(context);
        mResultHandler = new Handler(MAIN_EXECUTOR.getLooper());
    }

    @Override
    public void cancel(boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void doSearch(String query, SearchCallback<AdapterItem> callback) {
        mAppState.getModel().enqueueModelUpdateTask(new BaseModelUpdateTask() {
            @Override
            public void execute(LauncherAppState app, BgDataModel dataModel, AllAppsList apps) {
                ArrayList<AdapterItem> result = getTitleMatchResult(apps.data, query);
                mResultHandler.post(() -> callback.onSearchResult(query, result, null));
            }
        });
    }

    public ArrayList<AdapterItem> getResult(List<AppInfo> apps, String query) {
        return getTitleMatchResult(apps, query);
    }

    /**
     * Filters {@link AppInfo}s matching specified query
     */
    @AnyThread
    public ArrayList<AdapterItem> getTitleMatchResult(List<AppInfo> apps, String query) {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        final String queryTextLower = query.toLowerCase();
        final ArrayList<AdapterItem> result = new ArrayList<>();
        StringMatcherUtility.StringMatcher matcher =
                StringMatcherUtility.StringMatcher.getInstance();

        int resultCount = 0;
        int total = apps.size();
        for (int i = 0; i < total && resultCount < MAX_RESULTS_COUNT; i++) {
            AppInfo info = apps.get(i);
            if (StringMatcherUtility.matches(queryTextLower, info.title.toString(), matcher)) {
                AdapterItem appItem = AdapterItem.asApp(resultCount, "", info, resultCount);
                result.add(appItem);
                resultCount++;
            }
        }

        return result;
    }
}
