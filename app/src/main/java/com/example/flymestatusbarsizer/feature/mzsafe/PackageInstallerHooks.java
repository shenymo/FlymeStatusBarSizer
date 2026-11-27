package com.example.flymestatusbarsizer.feature.mzsafe;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.system.Os;
import android.text.Layout;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PackageInstallerHooks {
    private static final String ACTIVITY =
            "com.android.packageinstaller.FlymePackageInstallerActivity";
    private static final String DETAIL_PANEL_TAG = "flyme_sizer_apk_detail_panel";
    // Application-namespace key (>= 0x02000000) required by View.setTag(int, Object).
    private static final int DETAIL_VIEWS_TAG_KEY = 0x7f5a0001;
    private static final List<String> PREFERRED_ABI_ORDER = Arrays.asList(
            "arm64-v8a", "armeabi-v7a", "armeabi", "x86_64", "x86", "riscv64");
    private static final ExecutorService DETAIL_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PkgInstallerApkDetail");
        thread.setDaemon(true);
        return thread;
    });

    private PackageInstallerHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (!FlymeStatusBarSizer.isPackageInstallerSafetyBypassEnabled()) {
            return;
        }
        try {
            Class<?> activityClass = Class.forName(ACTIVITY, false, loader);
            Method scanMethod = findNoArgMethod(activityClass, "startInstallScan");
            Method installMethod = findNoArgMethod(activityClass, "doInstallFlyme");
            Method processPackageUriMethod = activityClass.getDeclaredMethod("processPackageUri", Uri.class);
            Method processPackageFileMethod = activityClass.getDeclaredMethod("processPackageFile", File.class);
            if (scanMethod == null || installMethod == null) {
                return;
            }
            Method updateViewMethod = activityClass.getDeclaredMethod("updateViewForNewState", int.class);
            Method updateButtonMethod = activityClass.getDeclaredMethod("updatePrimaryButton", int.class);
            Field virusFinishedField = activityClass.getDeclaredField("mIsVirusCheckFinish");
            Field virusSafeField = activityClass.getDeclaredField("mIsVirusCheckResultSafe");
            Field receivedStoreInfoField = activityClass.getDeclaredField("receivedMzStoreInfo");
            Field disposalField = activityClass.getDeclaredField("isDisposaled");
            Field continueInstallField = activityClass.getDeclaredField("needContinueInstall");
            Field storeInfoField = activityClass.getDeclaredField("mzStoreAppInfo");
            Field warningLayoutField = activityClass.getDeclaredField("mWarningLayout");
            Field primaryButtonField = activityClass.getDeclaredField("primaryButton");

            scanMethod.setAccessible(true);
            installMethod.setAccessible(true);
            processPackageUriMethod.setAccessible(true);
            processPackageFileMethod.setAccessible(true);
            updateViewMethod.setAccessible(true);
            updateButtonMethod.setAccessible(true);
            virusFinishedField.setAccessible(true);
            virusSafeField.setAccessible(true);
            receivedStoreInfoField.setAccessible(true);
            disposalField.setAccessible(true);
            continueInstallField.setAccessible(true);
            storeInfoField.setAccessible(true);
            warningLayoutField.setAccessible(true);
            primaryButtonField.setAccessible(true);

            module.intercept(processPackageUriMethod, chain -> {
                Object target = chain.getThisObject();
                Uri sourceUri = (Uri) chain.getArg(0);
                File sourceFile = resolveDirectDocumentFile((Activity) target, sourceUri);
                if (sourceFile == null) {
                    return chain.proceed();
                }
                processPackageFileMethod.invoke(target, sourceFile);
                return null;
            });

            module.intercept(scanMethod, chain -> {
                try {
                    Object activity = chain.getThisObject();
                    virusFinishedField.setBoolean(activity, true);
                    virusSafeField.setBoolean(activity, true);
                    receivedStoreInfoField.setBoolean(activity, true);
                    disposalField.setInt(activity, 0);
                    continueInstallField.setBoolean(activity, true);

                    Object storeInfo = storeInfoField.get(activity);
                    Field icpStatusField = storeInfo.getClass().getDeclaredField("icpStatus");
                    icpStatusField.setAccessible(true);
                    icpStatusField.setBoolean(storeInfo, true);

                    updateViewMethod.invoke(activity, 3);
                    updateButtonMethod.invoke(activity, 1);

                    View warningLayout = (View) warningLayoutField.get(activity);
                    warningLayout.setVisibility(View.GONE);
                    View primaryButton = (View) primaryButtonField.get(activity);
                    if (primaryButton instanceof TextView) {
                        TextView textButton = (TextView) primaryButton;
                        textButton.setText("安装");
                        textButton.setTextColor(Color.WHITE);
                    }
                    primaryButton.setOnClickListener(view -> {
                        try {
                            installMethod.invoke(activity);
                        } catch (Throwable error) {
                            module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                                    "Unable to start package installation", error);
                        }
                    });
                    return null;
                } catch (Throwable error) {
                    module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                            "Unable to prepare package installer confirmation", error);
                    return null;
                }
            });

            if (FlymeStatusBarSizer.isPackageInstallerDetailsEnabled()) {
                installApkDetailHooks(module, activityClass);
            }
        } catch (Throwable error) {
            module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                    "Unable to hook Flyme package installer", error);
        }
    }

    private static void installApkDetailHooks(FlymeStatusBarSizer module, Class<?> activityClass) {
        try {
            Method setupAppDetailMethod = findNoArgMethod(activityClass, "setupAppDetail");
            Method showContentMethod = activityClass.getDeclaredMethod("showContent", int.class);
            if (setupAppDetailMethod == null) {
                return;
            }
            DetailFields fields = new DetailFields(activityClass);
            setupAppDetailMethod.setAccessible(true);
            showContentMethod.setAccessible(true);

            module.intercept(setupAppDetailMethod, chain -> {
                Object result = chain.proceed();
                try {
                    Object target = chain.getThisObject();
                    if (target instanceof Activity) {
                        bindApkDetail((Activity) target, fields);
                    }
                } catch (Throwable error) {
                    module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                            "Unable to bind APK details", error);
                }
                return result;
            });

            module.intercept(showContentMethod, chain -> {
                Object result = chain.proceed();
                try {
                    Object target = chain.getThisObject();
                    int viewId = ((Integer) chain.getArg(0)).intValue();
                    if (target instanceof Activity) {
                        if (viewId == 0) {
                            bindApkDetail((Activity) target, fields);
                        }
                        updateDetailVisibility((Activity) target, fields, viewId == 0);
                    }
                } catch (Throwable error) {
                    module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                            "Unable to update APK details visibility", error);
                }
                return result;
            });
        } catch (Throwable error) {
            module.log(android.util.Log.WARN, "FlymeStatusBarSizer",
                    "Unable to hook APK details display", error);
        }
    }

    private static Method findNoArgMethod(Class<?> clazz, String name) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (name.equals(method.getName()) && method.getParameterTypes().length == 0) {
                return method;
            }
        }
        return null;
    }

    private static void bindApkDetail(Activity activity, DetailFields fields) throws Throwable {
        PackageInfo pkgInfo = (PackageInfo) fields.pkgInfo.get(activity);
        if (pkgInfo == null) {
            return;
        }
        PackageInfo installedPkgInfo = (PackageInfo) fields.installedPkgInfo.get(activity);
        ApplicationInfo installedAppInfo = (ApplicationInfo) fields.installedAppInfo.get(activity);
        File apkFile = (File) fields.apkFileToInstall.get(activity);
        File stagedFile = (File) fields.stagedApkFile.get(activity);
        Uri sourceUri = (Uri) fields.sourceUri.get(activity);
        TextView refVersionView = (TextView) fields.appVersionView.get(activity);

        DetailViews views = ensureDetailViews(activity, refVersionView);
        if (views == null) {
            return;
        }

        boolean isInstalled = isActuallyInstalled(installedPkgInfo, installedAppInfo);
        views.versionValue.setText(formatVersionText(pkgInfo, isInstalled ? installedPkgInfo : null));
        boolean needsAsyncPath = isContentUri(sourceUri);
        if (needsAsyncPath) {
            views.pathValue.setText("解析中...");
        } else {
            views.pathValue.setText(formatPathText(
                    resolveFileUriPath(sourceUri, stagedFile), sourceUri, apkFile, stagedFile));
        }

        SignatureData apkSigData = extractSignatureData(pkgInfo);
        SignatureData installedSigData = isInstalled
                ? extractSignatureData(installedPkgInfo)
                : SignatureData.EMPTY;
        boolean needsAsyncSignature = apkSigData.currentDigests.isEmpty()
                || (isInstalled && installedSigData.currentDigests.isEmpty());
        boolean needsSigningInfoRefresh = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && (pkgInfo.signingInfo == null
                || (isInstalled && installedPkgInfo.signingInfo == null));
        if (needsAsyncSignature && apkFile != null) {
            views.signatureValue.setText("解析中...");
        } else {
            views.signatureValue.setText(formatSignatureText(isInstalled, apkSigData, installedSigData));
        }

        if (apkFile != null || needsAsyncPath) {
            views.abiValue.setText(apkFile != null ? "解析中..." : "无本地 APK 文件");
            Object requestToken = new Object();
            views.abiValue.setTag(requestToken);
            String packageName = pkgInfo.packageName;
            boolean fetchApkSig = apkFile != null && (apkSigData.currentDigests.isEmpty()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && pkgInfo.signingInfo == null));
            boolean fetchInstalledSig = apkFile != null && isInstalled
                    && (installedSigData.currentDigests.isEmpty()
                    || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && installedPkgInfo.signingInfo == null));
            DETAIL_EXECUTOR.execute(() -> {
                String abiText = apkFile != null ? formatAbiText(extractApkAbis(apkFile)) : null;
                SignatureData resolvedApkSig = fetchApkSig
                        ? preferNonEmpty(extractArchiveSignatureData(activity, apkFile), apkSigData)
                        : apkSigData;
                SignatureData resolvedInstalledSig = fetchInstalledSig
                        ? preferNonEmpty(extractInstalledSignatureData(activity, packageName), installedSigData)
                        : installedSigData;
                String sigText = apkFile != null && (needsAsyncSignature || needsSigningInfoRefresh)
                        ? formatSignatureText(isInstalled, resolvedApkSig, resolvedInstalledSig)
                        : null;
                String pathText = needsAsyncPath
                        ? formatPathText(resolveContentUriPath(activity, sourceUri, stagedFile),
                                sourceUri, apkFile, stagedFile)
                        : null;
                views.container.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    if (views.abiValue.getTag() != requestToken) {
                        return;
                    }
                    if (abiText != null) {
                        views.abiValue.setText(abiText);
                    }
                    if (sigText != null) {
                        views.signatureValue.setText(sigText);
                    }
                    if (pathText != null) {
                        views.pathValue.setText(pathText);
                    }
                });
            });
        } else {
            views.abiValue.setTag(null);
            views.abiValue.setText("无本地 APK 文件");
        }

        syncDetailVisibilityWithCurrentContent(activity, fields, views.container);
    }

    private static void syncDetailVisibilityWithCurrentContent(
            Activity activity, DetailFields fields, View container) throws Throwable {
        View scanLayout = (View) fields.scanLoadingLayout.get(activity);
        View adInstallLayouts = (View) fields.adInstallLayouts.get(activity);
        boolean blockingContentVisible =
                (scanLayout != null && scanLayout.getVisibility() == View.VISIBLE)
                || (adInstallLayouts != null && adInstallLayouts.getVisibility() == View.VISIBLE);
        updateDetailVisibility(activity, fields, container, !blockingContentVisible);
    }

    private static void updateDetailVisibility(
            Activity activity, DetailFields fields, boolean showWhenIdle) throws Throwable {
        View container = findExistingDetailContainer(activity);
        if (container != null) {
            updateDetailVisibility(activity, fields, container, showWhenIdle);
        }
    }

    private static void updateDetailVisibility(
            Activity activity, DetailFields fields, View container, boolean showWhenIdle) throws Throwable {
        View topMessageLayout = (View) fields.topMessageLayout.get(activity);
        boolean topMessageVisible =
                topMessageLayout != null && topMessageLayout.getVisibility() == View.VISIBLE;
        container.setVisibility(showWhenIdle && !topMessageVisible ? View.VISIBLE : View.GONE);
    }

    private static ViewGroup findContentPanel(Activity activity) {
        if (activity == null) {
            return null;
        }
        String pkg = activity.getPackageName();
        int contentPanelId = activity.getResources().getIdentifier("install_content_panel", "id", pkg);
        if (contentPanelId == 0) {
            return null;
        }
        View panel = activity.findViewById(contentPanelId);
        return panel instanceof ViewGroup ? (ViewGroup) panel : null;
    }

    private static View findExistingDetailContainer(Activity activity) {
        ViewGroup panel = findContentPanel(activity);
        return panel != null ? panel.findViewWithTag(DETAIL_PANEL_TAG) : null;
    }

    private static DetailViews ensureDetailViews(Activity activity, TextView refVersionView) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();
        ViewGroup contentPanel = findContentPanel(activity);
        if (contentPanel == null) {
            return null;
        }
        View existing = contentPanel.findViewWithTag(DETAIL_PANEL_TAG);
        if (existing != null && existing.getTag(DETAIL_VIEWS_TAG_KEY) instanceof DetailViews) {
            return (DetailViews) existing.getTag(DETAIL_VIEWS_TAG_KEY);
        }

        int marginMedium = resolveDimenAttrOrFallback(
                activity, pkg, "marginMedium", "fd_sys_margin_medium", 16);
        int marginSmall = resolveDimenAttrOrFallback(
                activity, pkg, "marginSmall", "fd_sys_margin_small", 8);
        int dividerColor = resolveColorAttrOrFallback(
                activity, pkg, "colorOutlineVariant", "fd_sys_color_outline_variant_default", 0x0D000000);
        int textFallbackColor = resolveColorAttrOrFallback(
                activity, pkg, "colorOnSurfaceVariant", "fd_sys_color_on_surface_variant_default", 0x993C3C43);
        int styleId = res.getIdentifier("apk_detail_info", "style", pkg);
        int dimenId = res.getIdentifier("apk_detail_info_sp", "dimen", pkg);
        int labelGapPx = dpToPx(activity, 2);
        int rowPaddingVerticalPx = dpToPx(activity, 12);
        int dividerHeightPx = Math.max(1, dpToPx(activity, 1));

        LinearLayout card = new LinearLayout(activity);
        card.setTag(DETAIL_PANEL_TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        applyCardBackground(activity, pkg, card);
        card.setPadding(marginMedium, 0, marginMedium, 0);

        ViewGroup.MarginLayoutParams cardParams;
        if (contentPanel instanceof FrameLayout) {
            FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            frameParams.gravity = Gravity.TOP;
            cardParams = frameParams;
        } else {
            cardParams = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        cardParams.setMarginStart(marginMedium);
        cardParams.setMarginEnd(marginMedium);
        cardParams.bottomMargin = marginSmall;
        card.setLayoutParams(cardParams);

        ScrollView scrollView = new ScrollView(activity);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        scrollView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        LinearLayout rowsContainer = new LinearLayout(activity);
        rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView versionValue = addDetailRow(
                activity, rowsContainer, "版本", refVersionView,
                styleId, dimenId, textFallbackColor, labelGapPx, rowPaddingVerticalPx);
        addDivider(activity, rowsContainer, dividerColor, dividerHeightPx);
        TextView abiValue = addDetailRow(
                activity, rowsContainer, "支持 ABI", refVersionView,
                styleId, dimenId, textFallbackColor, labelGapPx, rowPaddingVerticalPx);
        addDivider(activity, rowsContainer, dividerColor, dividerHeightPx);
        TextView signatureValue = addDetailRow(
                activity, rowsContainer, "签名", refVersionView,
                styleId, dimenId, textFallbackColor, labelGapPx, rowPaddingVerticalPx);
        addDivider(activity, rowsContainer, dividerColor, dividerHeightPx);
        TextView pathValue = addDetailRow(
                activity, rowsContainer, "本地路径", refVersionView,
                styleId, dimenId, textFallbackColor, labelGapPx, rowPaddingVerticalPx);

        scrollView.addView(rowsContainer);
        card.addView(scrollView);

        DetailViews views = new DetailViews(card, versionValue, abiValue, signatureValue, pathValue);
        card.setTag(DETAIL_VIEWS_TAG_KEY, views);
        contentPanel.addView(card);
        return views;
    }

    private static TextView addDetailRow(
            Activity activity,
            LinearLayout parent,
            String label,
            TextView refVersionView,
            int styleId,
            int dimenId,
            int textFallbackColor,
            int labelGapPx,
            int rowPaddingVerticalPx) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, rowPaddingVerticalPx, 0, rowPaddingVerticalPx);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView labelView = createDetailTextView(
                activity, refVersionView, styleId, dimenId, textFallbackColor);
        labelView.setText(label);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = labelGapPx;
        labelView.setLayoutParams(labelParams);

        TextView valueView = createDetailTextView(
                activity, refVersionView, styleId, dimenId, textFallbackColor);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(labelView);
        row.addView(valueView);
        parent.addView(row);
        return valueView;
    }

    private static void addDivider(Activity activity, LinearLayout parent, int color, int heightPx) {
        View divider = new View(activity);
        divider.setBackgroundColor(color);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        parent.addView(divider);
    }

    private static TextView createDetailTextView(
            Activity activity,
            TextView refView,
            int styleId,
            int dimenId,
            int fallbackColor) {
        TextView textView = styleId != 0
                ? new TextView(activity, null, 0, styleId)
                : new TextView(activity);
        textView.setSingleLine(false);
        textView.setMaxLines(Integer.MAX_VALUE);
        textView.setEllipsize(null);
        textView.setHorizontallyScrolling(false);
        textView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        textView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        if (refView != null) {
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, refView.getTextSize());
            textView.setTextColor(refView.getTextColors());
            if (refView.getTypeface() != null) {
                textView.setTypeface(refView.getTypeface());
            }
            textView.setLetterSpacing(refView.getLetterSpacing());
        } else {
            if (dimenId != 0) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                        activity.getResources().getDimension(dimenId));
            } else {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            }
            textView.setTextColor(fallbackColor);
        }
        return textView;
    }

    private static void applyCardBackground(Activity activity, String pkg, View card) {
        int bgId = activity.getResources().getIdentifier("service_record_bg", "drawable", pkg);
        if (bgId != 0) {
            Drawable bg = activity.getDrawable(bgId);
            if (bg != null) {
                card.setBackground(bg);
                return;
            }
        }
        int bgColor = resolveColorAttrOrFallback(
                activity, pkg, "colorSurfaceContainerLow", null, 0x0A000000);
        GradientDrawable fallback = new GradientDrawable();
        fallback.setColor(bgColor);
        fallback.setCornerRadius(dpToPx(activity, 8));
        card.setBackground(fallback);
    }

    private static int resolveDimenAttrOrFallback(
            Activity activity, String pkg, String attrName, String dimenName, int fallbackDp) {
        Resources res = activity.getResources();
        int attrId = res.getIdentifier(attrName, "attr", pkg);
        if (attrId != 0) {
            TypedValue out = new TypedValue();
            if (activity.getTheme().resolveAttribute(attrId, out, true)
                    && out.type == TypedValue.TYPE_DIMENSION) {
                return TypedValue.complexToDimensionPixelSize(out.data, res.getDisplayMetrics());
            }
        }
        if (dimenName != null) {
            int dimenId = res.getIdentifier(dimenName, "dimen", pkg);
            if (dimenId != 0) {
                return res.getDimensionPixelSize(dimenId);
            }
        }
        return dpToPx(activity, fallbackDp);
    }

    private static int resolveColorAttrOrFallback(
            Activity activity, String pkg, String attrName, String colorName, int fallbackColor) {
        Resources res = activity.getResources();
        int attrId = res.getIdentifier(attrName, "attr", pkg);
        if (attrId != 0) {
            TypedValue out = new TypedValue();
            if (activity.getTheme().resolveAttribute(attrId, out, true)) {
                if (out.resourceId != 0) {
                    return activity.getColor(out.resourceId);
                }
                if (out.type >= TypedValue.TYPE_FIRST_COLOR_INT
                        && out.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                    return out.data;
                }
            }
        }
        if (colorName != null) {
            int colorId = res.getIdentifier(colorName, "color", pkg);
            if (colorId != 0) {
                return activity.getColor(colorId);
            }
        }
        return fallbackColor;
    }

    private static int dpToPx(Activity activity, int dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, activity.getResources().getDisplayMetrics()));
    }

    private static boolean isActuallyInstalled(PackageInfo installedPkgInfo, ApplicationInfo installedAppInfo) {
        if (installedPkgInfo == null) {
            return false;
        }
        ApplicationInfo info = installedAppInfo != null ? installedAppInfo : installedPkgInfo.applicationInfo;
        return info != null && (info.flags & ApplicationInfo.FLAG_INSTALLED) != 0;
    }

    private static String formatVersionText(PackageInfo newPkgInfo, PackageInfo installedPkgInfo) {
        String newVer = formatSingleVersion(newPkgInfo);
        if (installedPkgInfo == null) {
            return newVer;
        }
        return formatSingleVersion(installedPkgInfo) + " -> " + newVer;
    }

    private static String formatSingleVersion(PackageInfo pkgInfo) {
        String versionName = TextUtils.isEmpty(pkgInfo.versionName) ? "未知" : pkgInfo.versionName;
        long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? pkgInfo.getLongVersionCode()
                : pkgInfo.versionCode;
        return versionName + " (" + versionCode + ")";
    }

    private static boolean isContentUri(Uri uri) {
        return uri != null && ContentResolver.SCHEME_CONTENT.equalsIgnoreCase(uri.getScheme());
    }

    private static File resolveDirectDocumentFile(Activity activity, Uri uri) {
        if (activity == null || uri == null || !isContentUri(uri)) {
            return null;
        }
        try {
            if (!DocumentsContract.isDocumentUri(activity, uri)
                    || !"com.android.externalstorage.documents".equals(uri.getAuthority())) {
                return null;
            }
            String documentId = DocumentsContract.getDocumentId(uri);
            int separator = documentId.indexOf(':');
            if (separator <= 0) {
                return null;
            }
            String volume = documentId.substring(0, separator);
            String relativePath = documentId.substring(separator + 1);
            File baseDirectory;
            if ("primary".equalsIgnoreCase(volume)) {
                baseDirectory = Environment.getExternalStorageDirectory();
            } else {
                String volumePath = resolveStorageVolumePath(activity, volume, "", null);
                if (TextUtils.isEmpty(volumePath)) {
                    return null;
                }
                baseDirectory = new File(volumePath);
            }
            File sourceFile = new File(baseDirectory, relativePath);
            return sourceFile.isFile() && sourceFile.canRead() ? sourceFile : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resolveFileUriPath(Uri sourceUri, File stagedFile) {
        if (sourceUri != null && ContentResolver.SCHEME_FILE.equalsIgnoreCase(sourceUri.getScheme())) {
            return normalizeAndValidatePath(sourceUri.getPath(), stagedFile);
        }
        return null;
    }

    private static String resolveContentUriPath(Context context, Uri uri, File stagedFile) {
        if (context == null || uri == null) {
            return null;
        }
        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String docPath = resolveDocumentUriPath(context, uri, stagedFile);
                if (!TextUtils.isEmpty(docPath)) {
                    return docPath;
                }
            }
        } catch (Throwable ignored) {
        }
        String dataPath = queryDataColumn(context, uri, null, null, stagedFile);
        if (!TextUtils.isEmpty(dataPath)) {
            return dataPath;
        }
        return resolvePathFromFileDescriptor(context, uri, stagedFile);
    }

    private static String resolveDocumentUriPath(Context context, Uri uri, File stagedFile) {
        String authority = uri.getAuthority();
        String docId = DocumentsContract.getDocumentId(uri);
        if (TextUtils.isEmpty(docId)) {
            return null;
        }
        if ("com.android.externalstorage.documents".equals(authority)) {
            int colon = docId.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            String type = docId.substring(0, colon);
            String relPath = docId.substring(colon + 1);
            if ("primary".equalsIgnoreCase(type)) {
                return normalizeAndValidatePath(
                        joinPath(Environment.getExternalStorageDirectory().getAbsolutePath(), relPath),
                        stagedFile);
            }
            if ("home".equalsIgnoreCase(type)) {
                return normalizeAndValidatePath(
                        joinPath(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOCUMENTS).getAbsolutePath(), relPath),
                        stagedFile);
            }
            return resolveStorageVolumePath(context, type, relPath, stagedFile);
        }
        if ("com.android.providers.downloads.documents".equals(authority)) {
            if (docId.startsWith("raw:")) {
                return normalizeAndValidatePath(docId.substring(4), stagedFile);
            }
            String idPart = docId.startsWith("msf:") ? docId.substring(4) : docId;
            try {
                long id = Long.parseLong(idPart);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    String mediaDownloadPath = queryDataColumn(
                            context, MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            "_id=?", new String[]{idPart}, stagedFile);
                    if (!TextUtils.isEmpty(mediaDownloadPath)) {
                        return mediaDownloadPath;
                    }
                }
                for (String base : new String[]{
                        "content://downloads/public_downloads",
                        "content://downloads/my_downloads",
                        "content://downloads/all_downloads"}) {
                    Uri downloadUri = ContentUris.withAppendedId(Uri.parse(base), id);
                    String path = queryDataColumn(context, downloadUri, null, null, stagedFile);
                    if (!TextUtils.isEmpty(path)) {
                        return path;
                    }
                }
            } catch (NumberFormatException ignored) {
                return null;
            }
            return null;
        }
        if ("com.android.providers.media.documents".equals(authority)) {
            int colon = docId.indexOf(':');
            if (colon <= 0) {
                return null;
            }
            String type = docId.substring(0, colon);
            String id = docId.substring(colon + 1);
            Uri contentUri;
            if ("image".equals(type)) {
                contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            } else if ("video".equals(type)) {
                contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            } else if ("audio".equals(type)) {
                contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            } else {
                contentUri = MediaStore.Files.getContentUri("external");
            }
            return queryDataColumn(context, contentUri, "_id=?", new String[]{id}, stagedFile);
        }
        return null;
    }

    private static String resolveStorageVolumePath(
            Context context, String volumeUuid, String relPath, File stagedFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                StorageManager storageManager = context.getSystemService(StorageManager.class);
                if (storageManager != null) {
                    for (StorageVolume volume : storageManager.getStorageVolumes()) {
                        if (volume != null && volumeUuid.equalsIgnoreCase(volume.getUuid())) {
                            File dir = volume.getDirectory();
                            if (dir != null) {
                                String path = normalizeAndValidatePath(
                                        joinPath(dir.getAbsolutePath(), relPath), stagedFile);
                                if (!TextUtils.isEmpty(path)) {
                                    return path;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        String fallback = joinPath("/storage/" + volumeUuid, relPath);
        return new File(fallback).exists() ? normalizeAndValidatePath(fallback, stagedFile) : null;
    }

    @SuppressWarnings("deprecation")
    private static String queryDataColumn(
            Context context, Uri uri, String selection, String[] selectionArgs, File stagedFile) {
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{MediaStore.MediaColumns.DATA}, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                if (index >= 0) {
                    return normalizeAndValidatePath(cursor.getString(index), stagedFile);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String resolvePathFromFileDescriptor(Context context, Uri uri, File stagedFile) {
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd != null) {
                String link = Os.readlink("/proc/self/fd/" + pfd.getFd());
                return normalizeAndValidatePath(link, stagedFile);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String joinPath(String base, String relative) {
        if (TextUtils.isEmpty(relative)) {
            return base;
        }
        return relative.startsWith("/") ? base + relative : base + "/" + relative;
    }

    private static String normalizeAndValidatePath(String rawPath, File stagedFile) {
        if (TextUtils.isEmpty(rawPath)) {
            return null;
        }
        String path = rawPath.trim();
        if (!path.startsWith("/") || path.endsWith(" (deleted)")) {
            return null;
        }
        if (path.startsWith("/proc/") || path.startsWith("/dev/") || path.startsWith("/sys/")) {
            return null;
        }
        if (path.startsWith("/mnt/pass_through/") || path.startsWith("/mnt/user/")) {
            int emulatedIdx = path.indexOf("/emulated/");
            if (emulatedIdx > 0) {
                path = "/storage" + path.substring(emulatedIdx);
            }
        } else if (path.startsWith("/data/media/")) {
            String rest = path.substring("/data/media/".length());
            int slash = rest.indexOf('/');
            if (slash > 0 && TextUtils.isDigitsOnly(rest.substring(0, slash))) {
                path = "/storage/emulated/" + rest;
            }
        }
        if (stagedFile != null && path.equals(stagedFile.getAbsolutePath())) {
            return null;
        }
        return path;
    }

    private static String formatPathText(
            String resolvedOriginalPath, Uri sourceUri, File apkFile, File stagedFile) {
        if (!TextUtils.isEmpty(resolvedOriginalPath)) {
            return resolvedOriginalPath;
        }
        if (apkFile != null) {
            String path = apkFile.getAbsolutePath();
            boolean isStaged = stagedFile != null
                    && (stagedFile == apkFile || path.equals(stagedFile.getAbsolutePath()));
            return isStaged ? path + "（暂存文件）" : path;
        }
        if (isContentUri(sourceUri)) {
            return sourceUri.toString();
        }
        return "无本地 APK 文件";
    }

    private static Set<String> extractApkAbis(File apkFile) {
        Set<String> found = new TreeSet<>();
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name != null && name.startsWith("lib/")) {
                    int slash = name.indexOf('/', 4);
                    if (slash > 4 && slash + 1 < name.length()) {
                        String abi = name.substring(4, slash);
                        if (!abi.isEmpty()) {
                            found.add(abi);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        Set<String> ordered = new LinkedHashSet<>();
        for (String preferred : PREFERRED_ABI_ORDER) {
            if (found.remove(preferred)) {
                ordered.add(preferred);
            }
        }
        ordered.addAll(found);
        return ordered;
    }

    private static String formatAbiText(Set<String> abis) {
        if (abis == null) {
            return "无法读取";
        }
        if (abis.isEmpty()) {
            return "无原生库 (通用)";
        }
        return TextUtils.join(", ", abis);
    }

    private static SignatureData preferNonEmpty(SignatureData primary, SignatureData fallback) {
        return !primary.currentDigests.isEmpty() ? primary : fallback;
    }

    private static SignatureData extractArchiveSignatureData(Activity activity, File apkFile) {
        if (apkFile == null) {
            return SignatureData.EMPTY;
        }
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), getSigningQueryFlags());
            return extractSignatureData(info);
        } catch (Throwable ignored) {
            return SignatureData.EMPTY;
        }
    }

    private static SignatureData extractInstalledSignatureData(Activity activity, String packageName) {
        if (TextUtils.isEmpty(packageName)) {
            return SignatureData.EMPTY;
        }
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo info = pm.getPackageInfo(packageName, getSigningQueryFlags());
            return extractSignatureData(info);
        } catch (Throwable ignored) {
            return SignatureData.EMPTY;
        }
    }

    @SuppressWarnings("deprecation")
    private static int getSigningQueryFlags() {
        int flags = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        }
        return flags;
    }

    @SuppressWarnings("deprecation")
    private static SignatureData extractSignatureData(PackageInfo pkgInfo) {
        if (pkgInfo == null) {
            return SignatureData.EMPTY;
        }
        Signature[] currentSignatures = null;
        Signature[] historySignatures = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && pkgInfo.signingInfo != null) {
            SigningInfo signingInfo = pkgInfo.signingInfo;
            Signature[] contentsSigners = signingInfo.getApkContentsSigners();
            Signature[] history = signingInfo.getSigningCertificateHistory();
            if (contentsSigners != null && contentsSigners.length > 0) {
                currentSignatures = contentsSigners;
            } else {
                currentSignatures = history;
            }
            historySignatures = history;
        }
        if (currentSignatures == null || currentSignatures.length == 0) {
            currentSignatures = pkgInfo.signatures;
        }
        List<String> currentDigests = hashSignatures(currentSignatures);
        List<String> historyDigests = hashSignatures(historySignatures);
        if (currentDigests.isEmpty() && historyDigests.isEmpty()) {
            return SignatureData.EMPTY;
        }
        return new SignatureData(currentDigests, historyDigests);
    }

    private static List<String> hashSignatures(Signature[] signatures) {
        if (signatures == null || signatures.length == 0) {
            return Collections.emptyList();
        }
        Set<String> digests = new TreeSet<>();
        for (Signature signature : signatures) {
            if (signature == null) {
                continue;
            }
            String digest = sha256CertHex(signature.toByteArray());
            if (!TextUtils.isEmpty(digest)) {
                digests.add(digest);
            }
        }
        return digests.isEmpty() ? Collections.emptyList() : new ArrayList<>(digests);
    }

    private static String sha256CertHex(byte[] certBytes) {
        if (certBytes == null || certBytes.length == 0) {
            return "";
        }
        try {
            byte[] encoded = certBytes;
            try {
                CertificateFactory factory = CertificateFactory.getInstance("X.509");
                X509Certificate cert = (X509Certificate) factory.generateCertificate(
                        new ByteArrayInputStream(certBytes));
                if (cert != null) {
                    encoded = cert.getEncoded();
                }
            } catch (Throwable ignored) {
                // Fall back to raw Signature bytes if X.509 parsing fails.
            }
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(encoded);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format(Locale.ROOT, "%02x", b & 0xFF));
            }
            return sb.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String formatSignatureText(
            boolean isInstalled, SignatureData apkSig, SignatureData installedSig) {
        List<String> apkDigests = apkSig.currentDigests;
        List<String> installedDigests = installedSig.currentDigests;
        if (!isInstalled) {
            if (apkDigests.isEmpty()) {
                return "无法读取签名";
            }
            return "SHA-256: " + TextUtils.join("\nSHA-256: ", apkDigests);
        }
        if (!apkDigests.isEmpty() && !installedDigests.isEmpty()) {
            if (apkDigests.equals(installedDigests)) {
                return "一致（与已安装应用相同）\nSHA-256: "
                        + TextUtils.join("\nSHA-256: ", apkDigests);
            }
            if (!apkSig.historyDigests.isEmpty() && apkSig.historyDigests.containsAll(installedDigests)) {
                return "一致（证书轮换兼容）\n新包 SHA-256: "
                        + TextUtils.join(", ", apkDigests)
                        + "\n已安装 SHA-256: "
                        + TextUtils.join(", ", installedDigests);
            }
            return "不一致（与已安装应用不同）\n新包 SHA-256: "
                    + TextUtils.join(", ", apkDigests)
                    + "\n已安装 SHA-256: "
                    + TextUtils.join(", ", installedDigests);
        }
        return "无法比对\n新包 SHA-256: "
                + (apkDigests.isEmpty() ? "未知" : TextUtils.join(", ", apkDigests))
                + "\n已安装 SHA-256: "
                + (installedDigests.isEmpty() ? "未知" : TextUtils.join(", ", installedDigests));
    }

    private static final class SignatureData {
        static final SignatureData EMPTY =
                new SignatureData(Collections.emptyList(), Collections.emptyList());
        final List<String> currentDigests;
        final List<String> historyDigests;

        SignatureData(List<String> currentDigests, List<String> historyDigests) {
            this.currentDigests = currentDigests;
            this.historyDigests = historyDigests;
        }
    }

    private static final class DetailFields {
        final Field pkgInfo;
        final Field installedPkgInfo;
        final Field installedAppInfo;
        final Field apkFileToInstall;
        final Field stagedApkFile;
        final Field sourceUri;
        final Field appVersionView;
        final Field topMessageLayout;
        final Field scanLoadingLayout;
        final Field adInstallLayouts;

        DetailFields(Class<?> activityClass) throws NoSuchFieldException {
            pkgInfo = getAccessibleField(activityClass, "mPkgInfo");
            installedPkgInfo = getAccessibleField(activityClass, "mInstalledPkgInfo");
            installedAppInfo = getAccessibleField(activityClass, "mInstalledAppInfo");
            apkFileToInstall = getAccessibleField(activityClass, "apkFileToInstall");
            stagedApkFile = getAccessibleField(activityClass, "stagedApkFile");
            sourceUri = getAccessibleField(activityClass, "sourceURI");
            appVersionView = getAccessibleField(activityClass, "appVersionView");
            topMessageLayout = getAccessibleField(activityClass, "top_message_layout");
            scanLoadingLayout = getAccessibleField(activityClass, "scan_loading_layout");
            adInstallLayouts = getAccessibleField(activityClass, "ad_install_layouts");
        }

        private static Field getAccessibleField(Class<?> clazz, String name) throws NoSuchFieldException {
            Field field = clazz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        }
    }

    private static final class DetailViews {
        final View container;
        final TextView versionValue;
        final TextView abiValue;
        final TextView signatureValue;
        final TextView pathValue;

        DetailViews(
                View container,
                TextView versionValue,
                TextView abiValue,
                TextView signatureValue,
                TextView pathValue) {
            this.container = container;
            this.versionValue = versionValue;
            this.abiValue = abiValue;
            this.signatureValue = signatureValue;
            this.pathValue = pathValue;
        }
    }
}
