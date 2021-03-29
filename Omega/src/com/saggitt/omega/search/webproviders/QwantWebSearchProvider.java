/*
 *  Copyright (c) 2020 Omega Launcher
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package com.saggitt.omega.search.webproviders;

import android.content.Context;
import android.graphics.drawable.Drawable;

import com.android.launcher3.R;
import com.saggitt.omega.util.OmegaUtilsKt;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QwantWebSearchProvider extends WebSearchProvider {

    private Context mContext;

    public QwantWebSearchProvider(@NotNull Context context) {
        super(context);
        mContext = context;
    }

    @NotNull
    @Override
    public Drawable getIcon() {
        return getContext().getResources().getDrawable(R.drawable.ic_qwant);
    }

    @Override
    public String getPackageName() {
        return "https://www.qwant.com/?q=%s";
    }

    @Nullable
    @Override
    protected String getSuggestionsUrl() {
        return "https://api.qwant.com/api/suggest/?q=%s&client=opensearch&lang=" + OmegaUtilsKt.getLocale(mContext).getLanguage();
    }

    @NotNull
    @Override
    public String getName() {
        return getContext().getString(R.string.web_search_qwant);
    }
}
