package com.example.flymestatusbarsizer;

import android.view.View;

import java.util.WeakHashMap;

final class LauncherRecentsCurrentPageHelper {
    private static final WeakHashMap<Object, Boolean> SUPPRESS_REALTIME_SYNC = new WeakHashMap<>();

    private LauncherRecentsCurrentPageHelper() {
    }

    static void suppressRealtimeSyncUntilInteraction(Object recentsView) {
        if (recentsView != null) {
            SUPPRESS_REALTIME_SYNC.put(recentsView, Boolean.TRUE);
        }
    }

    static void allowRealtimeSync(Object recentsView) {
        if (recentsView != null) {
            SUPPRESS_REALTIME_SYNC.put(recentsView, Boolean.FALSE);
        }
    }

    static void clearRealtimeSyncSuppression(Object recentsView) {
        if (recentsView != null) {
            SUPPRESS_REALTIME_SYNC.remove(recentsView);
        }
    }

    static boolean isRealtimeSyncSuppressed(Object recentsView) {
        return Boolean.TRUE.equals(SUPPRESS_REALTIME_SYNC.get(recentsView));
    }

    static boolean syncCurrentPageToNearestTask(Object recentsView) {
        return syncCurrentPageInternal(recentsView, getNearestTaskPage(recentsView), false);
    }

    static boolean syncCurrentPageToRunningTask(Object recentsView) {
        return syncCurrentPageInternal(recentsView, getRunningTaskPage(recentsView), true);
    }

    private static boolean syncCurrentPageInternal(Object recentsView, int targetPage, boolean updateEvenIfSame) {
        if (recentsView == null || !isTaskPage(recentsView, targetPage)) {
            return false;
        }

        int oldPage = ReflectUtils.getIntField(recentsView, "mCurrentPage", -1);
        if (!updateEvenIfSame && oldPage == targetPage) {
            return false;
        }

        Object pageScrollValue = ReflectUtils.invokeMethod(
                recentsView, "getScrollForPage", new Class[]{int.class}, targetPage);
        if (!(pageScrollValue instanceof Integer)) {
            return false;
        }

        int currentScroll = getPrimaryScroll(recentsView);
        int pageScroll = (Integer) pageScrollValue;

        ReflectUtils.setIntField(recentsView, "mCurrentPage", targetPage);
        ReflectUtils.setIntField(recentsView, "mCurrentScrollOverPage", targetPage);
        ReflectUtils.setIntField(recentsView, "mCurrentPageScrollDiff", currentScroll - pageScroll);

        if (oldPage >= 0 && oldPage != targetPage) {
            ReflectUtils.invokeMethod(
                    recentsView, "notifyPageSwitchListener", new Class[]{int.class}, oldPage);
        }
        return true;
    }

    private static int getNearestTaskPage(Object recentsView) {
        int page = ReflectUtils.invokeNoArgInt(recentsView, "getPageNearestToCenterOfScreen", -1);
        if (isTaskPage(recentsView, page)) {
            return page;
        }
        Object taskView = ReflectUtils.invokeNoArg(recentsView, "getTaskViewNearestToCenterOfScreen");
        return getTaskPageIndex(recentsView, taskView);
    }

    private static int getRunningTaskPage(Object recentsView) {
        int page = ReflectUtils.invokeNoArgInt(recentsView, "getRunningTaskIndex", -1);
        if (isTaskPage(recentsView, page)) {
            return page;
        }
        Object taskView = ReflectUtils.invokeNoArg(recentsView, "getRunningTaskView");
        return getTaskPageIndex(recentsView, taskView);
    }

    private static int getTaskPageIndex(Object recentsView, Object taskView) {
        if (!(taskView instanceof View)) {
            return -1;
        }
        Object indexValue = ReflectUtils.invokeMethod(
                recentsView, "indexOfChild", new Class[]{View.class}, taskView);
        if (!(indexValue instanceof Integer)) {
            return -1;
        }
        int page = (Integer) indexValue;
        return isTaskPage(recentsView, page) ? page : -1;
    }

    private static boolean isTaskPage(Object recentsView, int page) {
        if (page < 0) {
            return false;
        }
        Object taskView = ReflectUtils.invokeMethod(
                recentsView, "getTaskViewAt", new Class[]{int.class}, page);
        return taskView instanceof View;
    }

    private static int getPrimaryScroll(Object recentsView) {
        Object orientationHandler = ReflectUtils.getField(recentsView, "mOrientationHandler");
        if (orientationHandler == null || !(recentsView instanceof View)) {
            return 0;
        }
        Object scrollValue = ReflectUtils.invokeMethod(
                orientationHandler, "getPrimaryScroll", new Class[]{View.class}, recentsView);
        return scrollValue instanceof Integer ? (Integer) scrollValue : 0;
    }
}
