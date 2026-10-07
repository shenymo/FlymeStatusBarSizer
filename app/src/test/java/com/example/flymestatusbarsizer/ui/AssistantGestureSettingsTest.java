package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.PopupMenu;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.config.SettingsStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowPopupMenu;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class AssistantGestureSettingsTest {
    private TestActivity activity;
    private View card;
    private static final String KEY = SettingsStore.KEY_ASSISTANT_GESTURE_SCENES;

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(TestActivity.class).get();
        activity.prefs().edit().clear().commit();
        Field prefs = MainActivity.class.getDeclaredField("prefs");
        prefs.setAccessible(true);
        prefs.set(activity, activity.prefs());
        card = new SettingsCardFactory(activity).createSideGestureSettingsCard();
    }

    @Test public void settingsCanBeReopenedWithoutChangingLegacySettings() {
        activity.prefs().edit().putBoolean(SettingsStore.KEY_ASSISTANT_GESTURE_ENABLED, true)
                .putInt(SettingsStore.KEY_SIDE_GESTURE_ACTION, SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT)
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_SIDE, SettingsStore.ASSISTANT_GESTURE_SIDE_RIGHT)
                .putInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, 180).commit();
        java.util.Map<String, ?> savedSettings = activity.prefs().getAll();
        card = new SettingsCardFactory(activity).createSideGestureSettingsCard();
        assertNotNull(byText(card, "侧边手势"));
        assertNotNull(byText(card, "触发动作"));
        assertNotNull(byText(card, "手势设置"));
        assertNotNull(byText(card, "负一屏外观"));
        assertNotNull(byText(card, "使用自选背景"));
        assertNotNull(byText(card, "右侧"));
        assertEquals(savedSettings, activity.prefs().getAll());

        card = new SettingsCardFactory(activity).createSideGestureSettingsCard();
        assertEquals(savedSettings, activity.prefs().getAll());
        assertTrue(gestureToggle().isChecked());
        assertEquals(180, activity.prefs().getInt(SettingsStore.KEY_ASSISTANT_GESTURE_DISTANCE_DP, -1));
        gestureToggle().setChecked(false);
        assertFalse(activity.prefs().getBoolean(SettingsStore.KEY_ASSISTANT_GESTURE_ENABLED, true));
    }

    private Switch gestureToggle() {
        View title = byText(card, "启用侧边手势");
        ViewGroup row = (ViewGroup) title.getParent().getParent();
        for (int i = 0; i < row.getChildCount(); i++) {
            if (row.getChildAt(i) instanceof Switch) return (Switch) row.getChildAt(i);
        }
        throw new AssertionError("Missing side gesture toggle");
    }

    @Test public void selectingTaskScaleChangesDescriptionAndHidesAssistantAppearance() {
        ((View) byText(card, "侧边手势").getParent()).performClick();
        View value = byText(card, "全局负一屏");
        assertNotNull(value);
        value.performClick();
        shadowOf(Looper.getMainLooper()).idle();
        PopupMenu popup = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popup);
        shadowOf(popup).getOnMenuItemClickListener().onMenuItemClick(
                popup.getMenu().findItem(SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE));
        assertEquals(SettingsStore.SIDE_GESTURE_ACTION_TASK_SCALE,
                activity.prefs().getInt(SettingsStore.KEY_SIDE_GESTURE_ACTION, -1));
        assertNotNull(byText(card, "应用单手缩放"));
        assertNotNull(byText(card, "从屏幕侧边向内滑动并按住，将当前应用缩小到右下角。"));
        assertFalse(isVisibleThroughParents(byText(card, "负一屏外观")));
        card = new SettingsCardFactory(activity).createSideGestureSettingsCard();
        assertNotNull(byText(card, "应用单手缩放"));
        ((View) byText(card, "侧边手势").getParent()).performClick();
        assertFalse(isVisibleThroughParents(byText(card, "负一屏外观")));
        byText(card, "应用单手缩放").performClick();
        shadowOf(Looper.getMainLooper()).idle();
        popup = ShadowPopupMenu.getLatestPopupMenu();
        shadowOf(popup).getOnMenuItemClickListener().onMenuItemClick(
                popup.getMenu().findItem(SettingsStore.SIDE_GESTURE_ACTION_GLOBAL_ASSISTANT));
        assertTrue(isVisibleThroughParents(byText(card, "负一屏外观")));
    }

    private boolean isVisibleThroughParents(View view) {
        assertNotNull(view);
        while (view != null) {
            if (view.getVisibility() != View.VISIBLE) return false;
            view = view.getParent() instanceof View ? (View) view.getParent() : null;
        }
        return true;
    }

    @Test public void defaultIsAllAndCancelDiscardsPendingChanges() {
        assertNotNull(byText(card, "常规界面、通知栏、控制中心"));
        AlertDialog dialog = open();
        for (int i = 0; i < 3; i++) assertTrue(dialog.getListView().isItemChecked(i));
        toggle(dialog, 1);
        assertFalse(activity.prefs().contains(KEY));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        assertFalse(activity.prefs().contains(KEY));
        assertNotNull(byText(card, "常规界面、通知栏、控制中心"));
        assertTrue(open().getListView().isItemChecked(1));
    }

    @Test public void confirmSavesCombinationAndEmptySelectionSurvivesReopening() {
        AlertDialog dialog = open();
        toggle(dialog, 1);
        confirm(dialog);
        assertEquals(5, activity.prefs().getInt(KEY, -1));
        assertNotNull(byText(card, "常规界面、控制中心"));
        dialog = open();
        assertFalse(dialog.getListView().isItemChecked(1));
        toggle(dialog, 0);
        toggle(dialog, 2);
        confirm(dialog);
        assertEquals(0, activity.prefs().getInt(KEY, -1));
        assertNotNull(byText(card, "未选择触发场景"));
        card = new SettingsCardFactory(activity).createSideGestureSettingsCard();
        assertNotNull(byText(card, "未选择触发场景"));
        dialog = open();
        for (int i = 0; i < 3; i++) assertFalse(dialog.getListView().isItemChecked(i));
        toggle(dialog, 1);
        confirm(dialog);
        assertEquals(2, activity.prefs().getInt(KEY, -1));
        assertNotNull(byText(card, "通知栏"));
    }

    private AlertDialog open() {
        View title = byText(card, "触发场景");
        assertNotNull(title);
        ((View) title.getParent()).performClick();
        shadowOf(Looper.getMainLooper()).idle();
        return ShadowAlertDialog.getLatestAlertDialog();
    }

    private void toggle(AlertDialog dialog, int index) {
        ListView list = dialog.getListView();
        list.performItemClick(list.getChildAt(index), index, list.getAdapter().getItemId(index));
    }

    private void confirm(AlertDialog dialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static View byText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = byText(group.getChildAt(i), text);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static class TestActivity extends MainActivity {
        @Override public SharedPreferences prefs() {
            return getSharedPreferences("assistant-settings-test", MODE_PRIVATE);
        }
        @Override public void putIntSetting(String key, int value) {
            prefs().edit().putInt(key, value).apply();
        }
    }
}
