/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CELLX;
import static com.android.launcher3.LauncherSettings.Favorites.CELLY;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_HOTSEAT;
import static com.android.launcher3.LauncherSettings.Favorites.ICON;
import static com.android.launcher3.LauncherSettings.Favorites.ICON_PACKAGE;
import static com.android.launcher3.LauncherSettings.Favorites.ICON_RESOURCE;
import static com.android.launcher3.LauncherSettings.Favorites.INTENT;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT;
import static com.android.launcher3.LauncherSettings.Favorites.PROFILE_ID;
import static com.android.launcher3.LauncherSettings.Favorites.RESTORED;
import static com.android.launcher3.LauncherSettings.Favorites.SCREEN;
import static com.android.launcher3.LauncherSettings.Favorites.TITLE;
import static com.android.launcher3.LauncherSettings.Favorites._ID;
import static com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.MatrixCursor;
import android.os.Process;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.LauncherSettings.Favorites;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.launcher3.util.Executors;
import com.android.launcher3.util.PackageManagerHelper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.LooperMode;
import org.robolectric.annotation.LooperMode.Mode;

/**
 * Tests for {@link LoaderCursor}
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(Mode.PAUSED)
public class LoaderCursorTest {

    private LauncherAppState mApp;

    private MatrixCursor mCursor;
    private InvariantDeviceProfile mIDP;
    private Context mContext;

    private LoaderCursor mLoaderCursor;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mIDP = InvariantDeviceProfile.INSTANCE.get(mContext);
        mApp = LauncherAppState.getInstance(mContext);

        mCursor = new MatrixCursor(new String[] {
                ICON, ICON_PACKAGE, ICON_RESOURCE, TITLE,
                _ID, CONTAINER, ITEM_TYPE, PROFILE_ID,
                SCREEN, CELLX, CELLY, RESTORED, INTENT
        });

        UserManagerState ums = new UserManagerState();
        mLoaderCursor = new LoaderCursor(mCursor, Favorites.CONTENT_URI, mApp, ums);
        ums.allUsers.put(0, Process.myUserHandle());
    }

    private void initCursor(int itemType, String title) {
        mCursor.newRow()
                .add(_ID, 1)
                .add(PROFILE_ID, 0)
                .add(ITEM_TYPE, itemType)
                .add(TITLE, title)
                .add(CONTAINER, CONTAINER_DESKTOP);
    }

    @Test
    public void getAppShortcutInfo_dontAllowMissing_invalidComponent() {
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());
        ComponentName cn = new ComponentName(mContext.getPackageName(), "placeholder-do");
        assertNull(mLoaderCursor.getAppShortcutInfo(
                new Intent().setComponent(cn), false /* allowMissingTarget */, true));
    }

    @Test
    public void getAppShortcutInfo_dontAllowMissing_validComponent() throws Exception {
        ComponentName cn = new ComponentName(TEST_PACKAGE, TEST_PACKAGE);
        shadowOf(mContext.getPackageManager()).addActivityIfNotPresent(cn);

        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());

        WorkspaceItemInfo info = Executors.MODEL_EXECUTOR.submit(() ->
                mLoaderCursor.getAppShortcutInfo(
                        new Intent().setComponent(cn), false  /* allowMissingTarget */, true))
                .get();
        assertNotNull(info);
        assertTrue(PackageManagerHelper.isLauncherAppTarget(info.getIntent()));
    }

    @Test
    public void getAppShortcutInfo_allowMissing_invalidComponent() throws Exception {
        initCursor(ITEM_TYPE_APPLICATION, "");
        assertTrue(mLoaderCursor.moveToNext());

        ComponentName cn = new ComponentName(mContext.getPackageName(), "placeholder-do");
        WorkspaceItemInfo info = Executors.MODEL_EXECUTOR.submit(() ->
                mLoaderCursor.getAppShortcutInfo(
                        new Intent().setComponent(cn), true  /* allowMissingTarget */, true))
                .get();
        assertNotNull(info);
        assertTrue(PackageManagerHelper.isLauncherAppTarget(info.getIntent()));
    }

    @Test
    public void loadSimpleShortcut() {
        initCursor(ITEM_TYPE_SHORTCUT, "my-shortcut");
        assertTrue(mLoaderCursor.moveToNext());

        WorkspaceItemInfo info = mLoaderCursor.loadSimpleWorkspaceItem();
        assertTrue(mApp.getIconCache().isDefaultIcon(info.bitmap, info.user));
        assertEquals("my-shortcut", info.title);
        assertEquals(ITEM_TYPE_SHORTCUT, info.itemType);
    }

    @Test
    public void checkItemPlacement_outsideBounds() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numDatabaseHotseatIcons = 3;

        // Item outside screen bounds are not placed
        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(4, 4, 1, 1, CONTAINER_DESKTOP, 1)));
    }

    @Test
    public void checkItemPlacement_overlappingItems() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numDatabaseHotseatIcons = 3;

        // Overlapping mItems are not placed
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 1)));
        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 1)));

        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 2)));
        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(0, 0, 1, 1, CONTAINER_DESKTOP, 2)));

        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(1, 1, 1, 1, CONTAINER_DESKTOP, 1)));
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(2, 2, 2, 2, CONTAINER_DESKTOP, 1)));

        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 2, 1, 2, CONTAINER_DESKTOP, 1)));
    }

    @Test
    public void checkItemPlacement_hotseat() {
        mIDP.numRows = 4;
        mIDP.numColumns = 4;
        mIDP.numDatabaseHotseatIcons = 3;

        // Hotseat mItems are only placed based on screenId
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 1)));
        assertTrue(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 2)));

        assertFalse(mLoaderCursor.checkItemPlacement(
                newItemInfo(3, 3, 1, 1, CONTAINER_HOTSEAT, 3)));
    }

    private ItemInfo newItemInfo(int cellX, int cellY, int spanX, int spanY,
            int container, int screenId) {
        ItemInfo info = new ItemInfo();
        info.cellX = cellX;
        info.cellY = cellY;
        info.spanX = spanX;
        info.spanY = spanY;
        info.container = container;
        info.screenId = screenId;
        return info;
    }
}
