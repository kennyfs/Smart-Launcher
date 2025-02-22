/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.settings;

import static android.content.pm.PackageManager.GET_RESOLVED_FILTER;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.android.launcher3.settings.SettingsActivity.EXTRA_FRAGMENT_ARG_KEY;
import static com.android.launcher3.uioverrides.plugins.PluginManagerWrapper.PLUGIN_CHANGED;
import static com.android.launcher3.uioverrides.plugins.PluginManagerWrapper.pluginEnabledKey;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.ArrayMap;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceDataStore;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.config.FlagTogglerPrefUi;
import com.android.launcher3.uioverrides.plugins.PluginManagerWrapper;
import com.android.launcher3.util.OnboardingPrefs;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dev-build only UI allowing developers to toggle flag settings and plugins.
 * See {@link FeatureFlags}.
 */
@TargetApi(Build.VERSION_CODES.O)
public class DeveloperOptionsFragment extends PreferenceFragmentCompat {

    private static final String ACTION_PLUGIN_SETTINGS = "com.android.systemui.action.PLUGIN_SETTINGS";
    private static final String PLUGIN_PERMISSION = "com.android.systemui.permission.PLUGIN";

    private final BroadcastReceiver mPluginReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadPluginPrefs();
        }
    };

    private PreferenceScreen mPreferenceScreen;

    private PreferenceCategory mPluginsCategory;
    private FlagTogglerPrefUi mFlagTogglerPrefUi;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        getContext().registerReceiver(mPluginReceiver, filter);
        getContext().registerReceiver(mPluginReceiver,
                new IntentFilter(Intent.ACTION_USER_UNLOCKED));

        mPreferenceScreen = getPreferenceManager().createPreferenceScreen(getContext());
        setPreferenceScreen(mPreferenceScreen);

        initFlags();
        loadPluginPrefs();
        maybeAddSandboxCategory();
        addOnboardingPrefsCatergory();

        if (getActivity() != null) {
            getActivity().setTitle("Developer Options");
        }
    }

    private void filterPreferences(String query, PreferenceGroup pg) {
        int count = pg.getPreferenceCount();
        int hidden = 0;
        for (int i = 0; i < count; i++) {
            Preference preference = pg.getPreference(i);
            if (preference instanceof PreferenceGroup) {
                filterPreferences(query, (PreferenceGroup) preference);
            } else {
                String title = preference.getTitle().toString().toLowerCase().replace("_", " ");
                if (query.isEmpty() || title.contains(query)) {
                    preference.setVisible(true);
                } else {
                    preference.setVisible(false);
                    hidden++;
                }
            }
        }
        pg.setVisible(hidden != count);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        EditText filterBox = view.findViewById(R.id.filter_box);
        filterBox.setVisibility(VISIBLE);
        filterBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                String query = editable.toString().toLowerCase().replace("_", " ");
                filterPreferences(query, mPreferenceScreen);
            }
        });

        if (getArguments() != null) {
            String filter = getArguments().getString(EXTRA_FRAGMENT_ARG_KEY);
            // Normally EXTRA_FRAGMENT_ARG_KEY is used to highlight the preference with the given
            // key. This is a slight variation where we instead filter by the human-readable titles.
            if (filter != null) {
                filterBox.setText(filter);
            }
        }

        View listView = getListView();
        final int bottomPadding = listView.getPaddingBottom();
        listView.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(
                    v.getPaddingLeft(),
                    v.getPaddingTop(),
                    v.getPaddingRight(),
                    bottomPadding + insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContext().unregisterReceiver(mPluginReceiver);
    }

    private PreferenceCategory newCategory(String title) {
        PreferenceCategory category = new PreferenceCategory(getContext());
        category.setOrder(Preference.DEFAULT_ORDER);
        category.setTitle(title);
        mPreferenceScreen.addPreference(category);
        return category;
    }

    private void initFlags() {
        if (!FeatureFlags.showFlagTogglerUi(getContext())) {
            return;
        }

        mFlagTogglerPrefUi = new FlagTogglerPrefUi(this);
        mFlagTogglerPrefUi.applyTo(newCategory("Feature flags"));
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (mFlagTogglerPrefUi != null) {
            mFlagTogglerPrefUi.onCreateOptionsMenu(menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mFlagTogglerPrefUi != null) {
            mFlagTogglerPrefUi.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStop() {
        if (mFlagTogglerPrefUi != null) {
            mFlagTogglerPrefUi.onStop();
        }
        super.onStop();
    }

    private void loadPluginPrefs() {
        if (mPluginsCategory != null) {
            mPreferenceScreen.removePreference(mPluginsCategory);
        }
        if (!PluginManagerWrapper.hasPlugins(getActivity())) {
            mPluginsCategory = null;
            return;
        }
        mPluginsCategory = newCategory("Plugins");

        PluginManagerWrapper manager = PluginManagerWrapper.INSTANCE.get(getContext());
        Context prefContext = getContext();
        PackageManager pm = getContext().getPackageManager();

        Set<String> pluginActions = manager.getPluginActions();

        ArrayMap<Pair<String, String>, ArrayList<Pair<String, ResolveInfo>>> plugins =
                new ArrayMap<>();

        Set<String> pluginPermissionApps = pm.getPackagesHoldingPermissions(
                        new String[]{PLUGIN_PERMISSION}, MATCH_DISABLED_COMPONENTS)
                .stream()
                .map(pi -> pi.packageName)
                .collect(Collectors.toSet());

        for (String action : pluginActions) {
            String name = toName(action);
            List<ResolveInfo> result = pm.queryIntentServices(
                    new Intent(action), MATCH_DISABLED_COMPONENTS | GET_RESOLVED_FILTER);
            for (ResolveInfo info : result) {
                String packageName = info.serviceInfo.packageName;
                if (!pluginPermissionApps.contains(packageName)) {
                    continue;
                }

                Pair<String, String> key = Pair.create(packageName, info.serviceInfo.processName);
                if (!plugins.containsKey(key)) {
                    plugins.put(key, new ArrayList<>());
                }
                plugins.get(key).add(Pair.create(name, info));
            }
        }

        PreferenceDataStore enabler = manager.getPluginEnabler();
        plugins.forEach((key, si) -> {
            String packageName = key.first;
            List<ComponentName> componentNames = si.stream()
                    .map(p -> new ComponentName(packageName, p.second.serviceInfo.name))
                    .collect(Collectors.toList());
            if (!componentNames.isEmpty()) {
                SwitchPreference pref = new PluginPreference(
                        prefContext, si.get(0).second, enabler, componentNames);
                pref.setSummary("Plugins: "
                        + si.stream().map(p -> p.first).collect(Collectors.joining(", ")));
                mPluginsCategory.addPreference(pref);
            }
        });
    }

    private void maybeAddSandboxCategory() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        Intent launchSandboxIntent =
                new Intent("com.android.quickstep.action.GESTURE_SANDBOX")
                        .setPackage(context.getPackageName())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (launchSandboxIntent.resolveActivity(context.getPackageManager()) == null) {
            return;
        }
        PreferenceCategory sandboxCategory = newCategory("Gesture Navigation Sandbox");
        sandboxCategory.setSummary("Learn and practice navigation gestures");
        Preference launchOnboardingTutorialPreference = new Preference(context);
        launchOnboardingTutorialPreference.setKey("launchOnboardingTutorial");
        launchOnboardingTutorialPreference.setTitle("Launch Onboarding Tutorial");
        launchOnboardingTutorialPreference.setSummary("Learn the basic navigation gestures.");
        launchOnboardingTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra(
                    "tutorial_steps",
                    new String[]{
                            "HOME_NAVIGATION",
                            "BACK_NAVIGATION",
                            "OVERVIEW_NAVIGATION"}));
            return true;
        });
        sandboxCategory.addPreference(launchOnboardingTutorialPreference);
        Preference launchBackTutorialPreference = new Preference(context);
        launchBackTutorialPreference.setKey("launchBackTutorial");
        launchBackTutorialPreference.setTitle("Launch Back Tutorial");
        launchBackTutorialPreference.setSummary("Learn how to use the Back gesture");
        launchBackTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra(
                    "tutorial_steps",
                    new String[]{"BACK_NAVIGATION"}));
            return true;
        });
        sandboxCategory.addPreference(launchBackTutorialPreference);
        Preference launchHomeTutorialPreference = new Preference(context);
        launchHomeTutorialPreference.setKey("launchHomeTutorial");
        launchHomeTutorialPreference.setTitle("Launch Home Tutorial");
        launchHomeTutorialPreference.setSummary("Learn how to use the Home gesture");
        launchHomeTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra(
                    "tutorial_steps",
                    new String[]{"HOME_NAVIGATION"}));
            return true;
        });
        sandboxCategory.addPreference(launchHomeTutorialPreference);
        Preference launchOverviewTutorialPreference = new Preference(context);
        launchOverviewTutorialPreference.setKey("launchOverviewTutorial");
        launchOverviewTutorialPreference.setTitle("Launch Overview Tutorial");
        launchOverviewTutorialPreference.setSummary("Learn how to use the Overview gesture");
        launchOverviewTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra(
                    "tutorial_steps",
                    new String[]{"OVERVIEW_NAVIGATION"}));
            return true;
        });
        sandboxCategory.addPreference(launchOverviewTutorialPreference);
        Preference launchAssistantTutorialPreference = new Preference(context);
        launchAssistantTutorialPreference.setKey("launchAssistantTutorial");
        launchAssistantTutorialPreference.setTitle("Launch Assistant Tutorial");
        launchAssistantTutorialPreference.setSummary("Learn how to use the Assistant gesture");
        launchAssistantTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra(
                    "tutorial_steps",
                    new String[]{"ASSISTANT"}));
            return true;
        });
        sandboxCategory.addPreference(launchAssistantTutorialPreference);
        Preference launchSandboxModeTutorialPreference = new Preference(context);
        launchSandboxModeTutorialPreference.setKey("launchSandboxMode");
        launchSandboxModeTutorialPreference.setTitle("Launch Sandbox Mode");
        launchSandboxModeTutorialPreference.setSummary("Practice navigation gestures");
        launchSandboxModeTutorialPreference.setOnPreferenceClickListener(preference -> {
            startActivity(launchSandboxIntent.putExtra(
                    "tutorial_steps",
                    new String[]{"SANDBOX_MODE"}));
            return true;
        });
        sandboxCategory.addPreference(launchSandboxModeTutorialPreference);
    }

    private void addOnboardingPrefsCatergory() {
        PreferenceCategory onboardingCategory = newCategory("Onboarding Flows");
        onboardingCategory.setSummary("Reset these if you want to see the education again.");
        for (Map.Entry<String, String[]> titleAndKeys : OnboardingPrefs.ALL_PREF_KEYS.entrySet()) {
            String title = titleAndKeys.getKey();
            String[] keys = titleAndKeys.getValue();
            Preference onboardingPref = new Preference(getContext());
            onboardingPref.setTitle(title);
            onboardingPref.setSummary("Tap to reset");
            onboardingPref.setOnPreferenceClickListener(preference -> {
                SharedPreferences.Editor sharedPrefsEdit = Utilities.getPrefs(getContext()).edit();
                for (String key : keys) {
                    sharedPrefsEdit.remove(key);
                }
                sharedPrefsEdit.apply();
                Toast.makeText(getContext(), "Reset " + title, Toast.LENGTH_SHORT).show();
                return true;
            });
            onboardingCategory.addPreference(onboardingPref);
        }
    }

    private String toName(String action) {
        String str = action.replace("com.android.systemui.action.PLUGIN_", "")
                .replace("com.android.launcher3.action.PLUGIN_", "");
        StringBuilder b = new StringBuilder();
        for (String s : str.split("_")) {
            if (b.length() != 0) {
                b.append(' ');
            }
            b.append(s.substring(0, 1));
            b.append(s.substring(1).toLowerCase());
        }
        return b.toString();
    }

    private static class PluginPreference extends SwitchPreference {
        private final String mPackageName;
        private final ResolveInfo mSettingsInfo;
        private final PreferenceDataStore mPluginEnabler;
        private final List<ComponentName> mComponentNames;

        PluginPreference(Context prefContext, ResolveInfo pluginInfo,
                         PreferenceDataStore pluginEnabler, List<ComponentName> componentNames) {
            super(prefContext);
            PackageManager pm = prefContext.getPackageManager();
            mPackageName = pluginInfo.serviceInfo.applicationInfo.packageName;
            Intent settingsIntent = new Intent(ACTION_PLUGIN_SETTINGS).setPackage(mPackageName);
            // If any Settings activity in app has category filters, set plugin action as category.
            List<ResolveInfo> settingsInfos =
                    pm.queryIntentActivities(settingsIntent, GET_RESOLVED_FILTER);
            if (pluginInfo.filter != null) {
                for (ResolveInfo settingsInfo : settingsInfos) {
                    if (settingsInfo.filter != null && settingsInfo.filter.countCategories() > 0) {
                        settingsIntent.addCategory(pluginInfo.filter.getAction(0));
                        break;
                    }
                }
            }

            mSettingsInfo = pm.resolveActivity(settingsIntent, 0);
            mPluginEnabler = pluginEnabler;
            mComponentNames = componentNames;
            setTitle(pluginInfo.loadLabel(pm));
            setChecked(isPluginEnabled());
            setWidgetLayoutResource(R.layout.switch_preference_with_settings);
        }

        private boolean isEnabled(ComponentName cn) {
            return mPluginEnabler.getBoolean(pluginEnabledKey(cn), true);

        }

        private boolean isPluginEnabled() {
            for (ComponentName componentName : mComponentNames) {
                if (!isEnabled(componentName)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        protected boolean persistBoolean(boolean isEnabled) {
            boolean shouldSendBroadcast = false;
            for (ComponentName componentName : mComponentNames) {
                if (isEnabled(componentName) != isEnabled) {
                    mPluginEnabler.putBoolean(pluginEnabledKey(componentName), isEnabled);
                    shouldSendBroadcast = true;
                }
            }
            if (shouldSendBroadcast) {
                final String pkg = mPackageName;
                final Intent intent = new Intent(PLUGIN_CHANGED,
                        pkg != null ? Uri.fromParts("package", pkg, null) : null);
                getContext().sendBroadcast(intent);
            }
            setChecked(isEnabled);
            return true;
        }

        @Override
        public void onBindViewHolder(PreferenceViewHolder holder) {
            super.onBindViewHolder(holder);
            boolean hasSettings = mSettingsInfo != null;
            holder.findViewById(R.id.settings).setVisibility(hasSettings ? VISIBLE : GONE);
            holder.findViewById(R.id.divider).setVisibility(hasSettings ? VISIBLE : GONE);
            holder.findViewById(R.id.settings).setOnClickListener(v -> {
                if (hasSettings) {
                    v.getContext().startActivity(new Intent().setComponent(
                            new ComponentName(mSettingsInfo.activityInfo.packageName,
                                    mSettingsInfo.activityInfo.name)));
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", mPackageName, null));
                getContext().startActivity(intent);
                return true;
            });
        }
    }
}
