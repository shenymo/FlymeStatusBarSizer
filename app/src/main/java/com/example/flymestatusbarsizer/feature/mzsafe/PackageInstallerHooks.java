package com.example.flymestatusbarsizer.feature.mzsafe;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

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
        TextView apkSizeView = (TextView) fields.apkSizeView.get(activity);
        hideHeaderVersionView(refVersionView, apkSizeView);

        DetailViews views = ensureDetailViews(activity, refVersionView);
        if (views == null) {
            return;
        }

        boolean isInstalled = isActuallyInstalled(installedPkgInfo, installedAppInfo);
        String detailIdentity = buildDetailIdentity(
                pkgInfo, installedPkgInfo, isInstalled, apkFile, sourceUri);
        if (TextUtils.equals(views.boundDetailIdentity, detailIdentity)) {
            syncDetailVisibilityWithCurrentContent(activity, fields, views.container);
            return;
        }
        views.boundDetailIdentity = detailIdentity;
        views.abiRow.valueView.setTag(null);
        setSignatureExpanded(views, false, false);
        clearCopyFeedback(views);

        bindVersionRows(views, pkgInfo, isInstalled ? installedPkgInfo : null);

        boolean needsAsyncPath = isContentUri(sourceUri);
        if (needsAsyncPath) {
            bindPendingPathRow(views, "解析中...");
        } else {
            bindPathRow(views, resolveFileUriPath(sourceUri, stagedFile), sourceUri, apkFile, stagedFile);
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
            bindPendingSignatureSection(views, "解析中...");
        } else {
            bindSignatureSection(views, isInstalled, apkSigData, installedSigData);
        }

        if (apkFile != null || needsAsyncPath) {
            views.abiRow.valueView.setText(apkFile != null ? "解析中..." : "无本地 APK 文件");
            Object requestToken = new Object();
            views.abiRow.valueView.setTag(requestToken);
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
                boolean shouldUpdateSig = apkFile != null && (needsAsyncSignature || needsSigningInfoRefresh);
                String resolvedContentPath = needsAsyncPath
                        ? resolveContentUriPath(activity, sourceUri, stagedFile)
                        : null;
                views.container.post(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    if (views.abiRow.valueView.getTag() != requestToken) {
                        return;
                    }
                    if (abiText != null) {
                        views.abiRow.valueView.setText(abiText);
                    }
                    if (shouldUpdateSig) {
                        bindSignatureSection(views, isInstalled, resolvedApkSig, resolvedInstalledSig);
                    }
                    if (needsAsyncPath) {
                        bindPathRow(views, resolvedContentPath, sourceUri, apkFile, stagedFile);
                    }
                });
            });
        } else {
            views.abiRow.valueView.setTag(null);
            views.abiRow.valueView.setText("无本地 APK 文件");
        }

        syncDetailVisibilityWithCurrentContent(activity, fields, views.container);
    }

    private static void hideHeaderVersionView(TextView appVersionView, TextView apkSizeView) {
        if (appVersionView != null && appVersionView.getVisibility() != View.GONE) {
            appVersionView.setVisibility(View.GONE);
        }
        if (apkSizeView != null && apkSizeView.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) apkSizeView.getLayoutParams();
            if (lp.getMarginStart() != 0) {
                lp.setMarginStart(0);
                apkSizeView.setLayoutParams(lp);
            }
        }
    }

    private static String buildApkIdentity(PackageInfo pkgInfo, File apkFile, Uri sourceUri) {
        StringBuilder sb = new StringBuilder();
        sb.append(pkgInfo.packageName != null ? pkgInfo.packageName : "");
        sb.append('|').append(getVersionCodeCompat(pkgInfo));
        sb.append('|').append(pkgInfo.versionName != null ? pkgInfo.versionName : "");
        sb.append('|').append(apkFile != null ? apkFile.getAbsolutePath() : "");
        sb.append('|').append(sourceUri != null ? sourceUri.toString() : "");
        return sb.toString();
    }

    private static String buildDetailIdentity(
            PackageInfo pkgInfo,
            PackageInfo installedPkgInfo,
            boolean isInstalled,
            File apkFile,
            Uri sourceUri) {
        StringBuilder sb = new StringBuilder(buildApkIdentity(pkgInfo, apkFile, sourceUri));
        sb.append('|').append(isInstalled);
        if (isInstalled && installedPkgInfo != null) {
            sb.append('|').append(installedPkgInfo.packageName != null
                    ? installedPkgInfo.packageName : "");
            sb.append('|').append(getVersionCodeCompat(installedPkgInfo));
            sb.append('|').append(installedPkgInfo.versionName != null
                    ? installedPkgInfo.versionName : "");
        }
        return sb.toString();
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
        String pkg = activity.getPackageName();
        ViewGroup contentPanel = findContentPanel(activity);
        if (contentPanel == null) {
            return null;
        }
        View existing = contentPanel.findViewWithTag(DETAIL_PANEL_TAG);
        if (existing != null && existing.getTag(DETAIL_VIEWS_TAG_KEY) instanceof DetailViews) {
            return (DetailViews) existing.getTag(DETAIL_VIEWS_TAG_KEY);
        }

        DetailTheme theme = DetailTheme.resolve(activity, pkg, refVersionView);

        LinearLayout card = new LinearLayout(activity);
        card.setTag(DETAIL_PANEL_TAG);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(null);
        card.setPadding(theme.cardHorizontalPaddingPx, 0, theme.cardHorizontalPaddingPx, 0);

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
        cardParams.setMarginStart(theme.marginMediumPx);
        cardParams.setMarginEnd(theme.marginMediumPx);
        cardParams.bottomMargin = theme.marginSmallPx;
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
        rowsContainer.setPadding(0, theme.marginSmallPx / 2, 0, theme.marginSmallPx / 2);

        DetailRow installedVersionRow = createDetailRow(activity, rowsContainer, "当前版本", theme, true);
        View installedVersionDivider = addDivider(activity, rowsContainer, theme.dividerColor, theme.dividerHeightPx);
        DetailRow targetVersionRow = createDetailRow(activity, rowsContainer, "待安装版本", theme, true);
        addDivider(activity, rowsContainer, theme.dividerColor, theme.dividerHeightPx);
        DetailRow abiRow = createDetailRow(activity, rowsContainer, "支持 ABI", theme, false);
        addDivider(activity, rowsContainer, theme.dividerColor, theme.dividerHeightPx);

        SignatureSection signatureSection = createSignatureSection(activity, rowsContainer, theme);
        addDivider(activity, rowsContainer, theme.dividerColor, theme.dividerHeightPx);
        DetailRow pathRow = createDetailRow(activity, rowsContainer, "本地路径", theme, true);

        scrollView.addView(rowsContainer);
        card.addView(scrollView);

        DetailViews views = new DetailViews(
                activity,
                card,
                theme,
                installedVersionRow,
                installedVersionDivider,
                targetVersionRow,
                abiRow,
                signatureSection,
                pathRow);

        bindCopyLongPress(views, installedVersionRow.rowView);
        bindCopyLongPress(views, targetVersionRow.rowView);
        bindCopyLongPress(views, pathRow.rowView);
        bindCopyLongPress(views, signatureSection.headerRowView);
        bindCopyLongPress(views, signatureSection.apkDigestRow.rowView);
        bindCopyLongPress(views, signatureSection.installedDigestRow.rowView);

        signatureSection.headerRowView.setOnClickListener(v ->
                setSignatureExpanded(views, !views.signatureExpanded, true));

        card.setTag(DETAIL_VIEWS_TAG_KEY, views);
        contentPanel.addView(card);
        return views;
    }

    private static DetailRow createDetailRow(
            Activity activity,
            LinearLayout parent,
            String label,
            DetailTheme theme,
            boolean interactive) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(theme.rowHorizontalPaddingPx, theme.rowPaddingVerticalPx,
                theme.rowHorizontalPaddingPx, theme.rowPaddingVerticalPx);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        if (interactive) {
            applyInteractiveForeground(activity, row, theme);
            row.setLongClickable(true);
        }

        TextView labelView = createStyledTextView(activity, theme, theme.labelTextSizePx, theme.labelColor);
        labelView.setText(label);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = theme.labelGapPx;
        labelView.setLayoutParams(labelParams);

        TextView valueView = createStyledTextView(activity, theme, theme.valueTextSizePx, theme.valueColor);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(labelView);
        row.addView(valueView);
        parent.addView(row);
        return new DetailRow(row, labelView, valueView);
    }

    private static SignatureSection createSignatureSection(
            Activity activity,
            LinearLayout parent,
            DetailTheme theme) {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout headerRow = new LinearLayout(activity);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(theme.rowHorizontalPaddingPx, theme.rowPaddingVerticalPx,
                theme.rowHorizontalPaddingPx, theme.rowPaddingVerticalPx);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        applyInteractiveForeground(activity, headerRow, theme);
        headerRow.setClickable(true);
        headerRow.setLongClickable(true);

        LinearLayout leftColumn = new LinearLayout(activity);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        leftColumn.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView labelView = createStyledTextView(activity, theme, theme.labelTextSizePx, theme.labelColor);
        labelView.setText("签名");
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = theme.labelGapPx;
        labelView.setLayoutParams(labelParams);

        TextView summaryView = createStyledTextView(activity, theme, theme.valueTextSizePx, theme.valueColor);
        summaryView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        leftColumn.addView(labelView);
        leftColumn.addView(summaryView);

        ImageView arrowView = createExpandArrowView(activity, theme);
        TextView arrowFallbackView = null;
        if (arrowView == null) {
            arrowFallbackView = createStyledTextView(activity, theme, theme.labelTextSizePx, theme.labelColor);
            arrowFallbackView.setText("展开");
            LinearLayout.LayoutParams fallbackParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            fallbackParams.setMarginStart(theme.marginSmallPx);
            arrowFallbackView.setLayoutParams(fallbackParams);
        }

        headerRow.addView(leftColumn);
        if (arrowView != null) {
            headerRow.addView(arrowView);
        } else {
            headerRow.addView(arrowFallbackView);
        }

        LinearLayout digestContainer = new LinearLayout(activity);
        digestContainer.setOrientation(LinearLayout.VERTICAL);
        digestContainer.setVisibility(View.GONE);
        digestContainer.setPadding(0, 0, 0, theme.marginSmallPx);
        digestContainer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        DetailRow apkDigestRow = createDigestSubRow(activity, digestContainer, "SHA-256", theme);
        DetailRow installedDigestRow = createDigestSubRow(activity, digestContainer, "已安装 SHA-256", theme);

        container.addView(headerRow);
        container.addView(digestContainer);
        parent.addView(container);

        return new SignatureSection(
                headerRow,
                summaryView,
                arrowView,
                arrowFallbackView,
                digestContainer,
                apkDigestRow,
                installedDigestRow);
    }

    private static DetailRow createDigestSubRow(
            Activity activity,
            LinearLayout parent,
            String label,
            DetailTheme theme) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(theme.rowHorizontalPaddingPx, theme.subRowPaddingVerticalPx,
                theme.rowHorizontalPaddingPx, theme.subRowPaddingVerticalPx);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = theme.labelGapPx;
        row.setLayoutParams(rowParams);
        applySubtleContainerBackground(row, theme.subCardBgColor, theme.highlightCornerRadiusPx);
        applyInteractiveForeground(activity, row, theme);
        row.setLongClickable(true);

        TextView labelView = createStyledTextView(activity, theme, theme.subLabelTextSizePx, theme.labelColor);
        labelView.setText(label);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.bottomMargin = theme.labelGapPx;
        labelView.setLayoutParams(labelParams);

        TextView valueView = createStyledTextView(activity, theme, theme.digestTextSizePx, theme.valueColor);
        valueView.setTypeface(Typeface.MONOSPACE);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        row.addView(labelView);
        row.addView(valueView);
        parent.addView(row);
        return new DetailRow(row, labelView, valueView);
    }

    private static ImageView createExpandArrowView(Activity activity, DetailTheme theme) {
        Resources res = activity.getResources();
        String pkg = activity.getPackageName();
        int arrowId = res.getIdentifier("service_record_arrow", "drawable", pkg);
        if (arrowId == 0) {
            arrowId = res.getIdentifier("mz_arrow_next_right", "drawable", pkg);
        }
        if (arrowId == 0) {
            return null;
        }
        Drawable arrowDrawable = activity.getDrawable(arrowId);
        if (arrowDrawable == null) {
            return null;
        }
        ImageView arrow = new ImageView(activity);
        arrow.setImageDrawable(arrowDrawable);
        arrow.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int sizePx = dpToPx(activity, 18);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
        params.setMarginStart(theme.marginSmallPx);
        arrow.setLayoutParams(params);
        return arrow;
    }

    private static View addDivider(Activity activity, LinearLayout parent, int color, int heightPx) {
        View divider = new View(activity);
        divider.setBackgroundColor(color);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx));
        parent.addView(divider);
        return divider;
    }

    private static TextView createStyledTextView(
            Activity activity,
            DetailTheme theme,
            float textSizePx,
            int textColor) {
        TextView textView = theme.styleId != 0
                ? new TextView(activity, null, 0, theme.styleId)
                : new TextView(activity);
        textView.setSingleLine(false);
        textView.setMaxLines(Integer.MAX_VALUE);
        textView.setEllipsize(null);
        textView.setHorizontallyScrolling(false);
        textView.setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE);
        textView.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx);
        textView.setTextColor(textColor);
        if (theme.baseTypeface != null) {
            textView.setTypeface(theme.baseTypeface);
        }
        textView.setLetterSpacing(theme.letterSpacing);
        return textView;
    }

    private static void applyInteractiveForeground(Activity activity, View view, DetailTheme theme) {
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(theme.highlightCornerRadiusPx);

        TypedValue out = new TypedValue();
        if (activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                && out.resourceId != 0) {
            Drawable drawable = activity.getDrawable(out.resourceId);
            if (drawable != null) {
                Drawable mutated = drawable.mutate();
                if (mutated instanceof RippleDrawable
                        && ((RippleDrawable) mutated).setDrawableByLayerId(android.R.id.mask, mask)) {
                    view.setForeground(mutated);
                    return;
                }
            }
        }

        int highlightColor = Color.argb(
                0x24,
                Color.red(theme.primaryAccentColor),
                Color.green(theme.primaryAccentColor),
                Color.blue(theme.primaryAccentColor));
        if (activity.getTheme().resolveAttribute(android.R.attr.colorControlHighlight, out, true)) {
            if (out.resourceId != 0) {
                highlightColor = activity.getColor(out.resourceId);
            } else if (out.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && out.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                highlightColor = out.data;
            }
        }
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(highlightColor), null, mask));
    }

    private static void applySubtleContainerBackground(View view, int color, int cornerRadiusPx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(cornerRadiusPx);
        view.setTag(BASE_BG_TAG_KEY, bg);
        view.setBackground(bg);
    }

    private static final int COPY_TARGET_TAG_KEY = 0x7f5a0002;
    private static final int BASE_BG_TAG_KEY = 0x7f5a0003;

    private static void bindCopyLongPress(DetailViews views, View targetView) {
        targetView.setOnLongClickListener(v -> {
            Object tag = v.getTag(COPY_TARGET_TAG_KEY);
            if (!(tag instanceof CopyTarget)) {
                return false;
            }
            CopyTarget copyTarget = (CopyTarget) tag;
            handleCopyTarget(views, v, copyTarget);
            return true;
        });
    }

    private static void setCopyTarget(View targetView, CopyTarget copyTarget) {
        targetView.setTag(COPY_TARGET_TAG_KEY, copyTarget);
    }

    private static void handleCopyTarget(
            DetailViews views,
            View targetView,
            CopyTarget copyTarget) {
        targetView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (!copyTarget.isCopyable()) {
            showCopyFeedback(views, targetView, false);
            return;
        }
        ClipboardManager clipboard = views.activity.getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            showCopyFeedback(views, targetView, false);
            return;
        }
        try {
            clipboard.setPrimaryClip(ClipData.newPlainText(copyTarget.label, copyTarget.value));
        } catch (Throwable ignored) {
            showCopyFeedback(views, targetView, false);
            return;
        }
        showCopyFeedback(views, targetView, true);
    }

    private static void showCopyFeedback(
            DetailViews views,
            View targetView,
            boolean copied) {
        clearCopyFeedback(views);

        GradientDrawable highlightBg = new GradientDrawable();
        highlightBg.setColor(copied ? views.theme.primaryContainerColor : views.theme.errorContainerColor);
        highlightBg.setCornerRadius(views.theme.highlightCornerRadiusPx);
        targetView.setBackground(highlightBg);
        views.activeHighlightView = targetView;

        if (views.activeToast != null) {
            views.activeToast.cancel();
        }
        views.activeToast = Toast.makeText(
                views.activity,
                copied ? "已复制" : "无法复制",
                Toast.LENGTH_SHORT);
        views.activeToast.show();

        Runnable resetTask = () -> clearCopyFeedback(views);
        views.pendingResetRunnable = resetTask;
        views.container.postDelayed(resetTask, 800L);
    }

    private static void clearCopyFeedback(DetailViews views) {
        if (views.pendingResetRunnable != null) {
            views.container.removeCallbacks(views.pendingResetRunnable);
            views.pendingResetRunnable = null;
        }
        if (views.activeHighlightView != null) {
            Object baseBg = views.activeHighlightView.getTag(BASE_BG_TAG_KEY);
            views.activeHighlightView.setBackground(baseBg instanceof Drawable ? (Drawable) baseBg : null);
            views.activeHighlightView = null;
        }
    }

    private static void setSignatureExpanded(DetailViews views, boolean expanded, boolean animate) {
        views.signatureExpanded = expanded;
        boolean hasExpandableDetails = views.signatureSection.hasExpandableDetails;
        views.signatureSection.digestContainer.setVisibility(
                expanded && hasExpandableDetails ? View.VISIBLE : View.GONE);
        float targetRotation = expanded ? 90f : 0f;
        if (views.signatureSection.arrowView != null) {
            if (animate) {
                views.signatureSection.arrowView.animate()
                        .rotation(targetRotation)
                        .setDuration(150L)
                        .start();
            } else {
                views.signatureSection.arrowView.animate().cancel();
                views.signatureSection.arrowView.setRotation(targetRotation);
            }
        } else if (views.signatureSection.arrowFallbackView != null) {
            views.signatureSection.arrowFallbackView.setText(expanded ? "收起" : "展开");
        }
    }

    private static int resolveDimenAttrOrFallback(
            Activity activity, String pkg, String attrName, String dimenName, int fallbackDp) {
        Resources res = activity.getResources();
        int attrId = attrName != null ? res.getIdentifier(attrName, "attr", pkg) : 0;
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
        int attrId = attrName != null ? res.getIdentifier(attrName, "attr", pkg) : 0;
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

    private static void bindVersionRows(
            DetailViews views, PackageInfo newPkgInfo, PackageInfo installedPkgInfo) {
        String newVersionName = safeVersionName(newPkgInfo);
        long newVersionCode = getVersionCodeCompat(newPkgInfo);
        String newRawVersion = formatRawVersion(newVersionName, newVersionCode);
        setCopyTarget(views.targetVersionRow.rowView,
                CopyTarget.of("待安装版本", newRawVersion));

        if (installedPkgInfo == null) {
            views.installedVersionRow.rowView.setVisibility(View.GONE);
            views.installedVersionDivider.setVisibility(View.GONE);
            setCopyTarget(views.installedVersionRow.rowView,
                    CopyTarget.unavailable("当前版本"));
            views.targetVersionRow.valueView.setText(newRawVersion);
            return;
        }

        String installedVersionName = safeVersionName(installedPkgInfo);
        long installedVersionCode = getVersionCodeCompat(installedPkgInfo);
        String installedRawVersion = formatRawVersion(installedVersionName, installedVersionCode);
        setCopyTarget(views.installedVersionRow.rowView,
                CopyTarget.of("当前版本", installedRawVersion));

        views.installedVersionRow.rowView.setVisibility(View.VISIBLE);
        views.installedVersionDivider.setVisibility(View.VISIBLE);

        boolean isDowngrade = newVersionCode < installedVersionCode;
        int targetHighlightColor = isDowngrade
                ? views.theme.warningColor
                : views.theme.primaryAccentColor;
        int installedHighlightColor = views.theme.installedVersionDiffColor;

        SpannableStringBuilder installedDisplay = new SpannableStringBuilder();
        SpannableStringBuilder targetDisplay = new SpannableStringBuilder();
        appendDiffHighlightedVersionNames(
                installedVersionName, newVersionName,
                installedHighlightColor, targetHighlightColor,
                installedDisplay, targetDisplay);

        installedDisplay.append(" (");
        targetDisplay.append(" (");
        boolean codeDiffers = installedVersionCode != newVersionCode;
        appendHighlightedSegment(installedDisplay, String.valueOf(installedVersionCode),
                codeDiffers, installedHighlightColor);
        appendHighlightedSegment(targetDisplay, String.valueOf(newVersionCode),
                codeDiffers, targetHighlightColor);
        installedDisplay.append(')');
        targetDisplay.append(')');

        if (isDowngrade) {
            int start = targetDisplay.length();
            targetDisplay.append("  降级安装");
            targetDisplay.setSpan(new ForegroundColorSpan(views.theme.warningColor),
                    start + 2, targetDisplay.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            targetDisplay.setSpan(new StyleSpan(Typeface.BOLD),
                    start + 2, targetDisplay.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        views.installedVersionRow.valueView.setText(installedDisplay);
        views.targetVersionRow.valueView.setText(targetDisplay);
    }

    private static String safeVersionName(PackageInfo pkgInfo) {
        return TextUtils.isEmpty(pkgInfo.versionName) ? "未知" : pkgInfo.versionName;
    }

    @SuppressWarnings("deprecation")
    private static long getVersionCodeCompat(PackageInfo pkgInfo) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? pkgInfo.getLongVersionCode()
                : pkgInfo.versionCode;
    }

    private static String formatRawVersion(String versionName, long versionCode) {
        return versionName + " (" + versionCode + ")";
    }

    private static void appendDiffHighlightedVersionNames(
            String installedName,
            String targetName,
            int installedColor,
            int targetColor,
            SpannableStringBuilder installedOut,
            SpannableStringBuilder targetOut) {
        if (TextUtils.equals(installedName, targetName)) {
            installedOut.append(installedName);
            targetOut.append(targetName);
            return;
        }
        List<VersionToken> installedTokens = tokenizeVersionName(installedName);
        List<VersionToken> targetTokens = tokenizeVersionName(targetName);
        int maxCount = Math.max(installedTokens.size(), targetTokens.size());
        for (int i = 0; i < maxCount; i++) {
            VersionToken oldTok = i < installedTokens.size() ? installedTokens.get(i) : null;
            VersionToken newTok = i < targetTokens.size() ? targetTokens.get(i) : null;
            if (oldTok != null && newTok != null) {
                if (TextUtils.equals(oldTok.text, newTok.text)) {
                    installedOut.append(oldTok.text);
                    targetOut.append(newTok.text);
                } else if (!oldTok.isSeparator && !newTok.isSeparator) {
                    appendTokenDiff(oldTok.text, newTok.text,
                            installedColor, targetColor, installedOut, targetOut);
                } else {
                    appendHighlightedSegment(installedOut, oldTok.text, !oldTok.isSeparator, installedColor);
                    appendHighlightedSegment(targetOut, newTok.text, !newTok.isSeparator, targetColor);
                }
            } else if (oldTok != null) {
                appendHighlightedSegment(installedOut, oldTok.text, true, installedColor);
            } else {
                appendHighlightedSegment(targetOut, newTok.text, true, targetColor);
            }
        }
    }

    private static void appendTokenDiff(
            String oldText,
            String newText,
            int installedColor,
            int targetColor,
            SpannableStringBuilder installedOut,
            SpannableStringBuilder targetOut) {
        int minLen = Math.min(oldText.length(), newText.length());
        int prefix = 0;
        while (prefix < minLen && oldText.charAt(prefix) == newText.charAt(prefix)) {
            prefix++;
        }
        int suffix = 0;
        while (suffix < (minLen - prefix)
                && oldText.charAt(oldText.length() - 1 - suffix)
                == newText.charAt(newText.length() - 1 - suffix)) {
            suffix++;
        }
        boolean allowSubTokenTrim = (!TextUtils.isDigitsOnly(oldText) || !TextUtils.isDigitsOnly(newText)
                || minLen > 4) && (prefix + suffix) >= (minLen / 2) && prefix + suffix > 0;
        if (!allowSubTokenTrim) {
            appendHighlightedSegment(installedOut, oldText, true, installedColor);
            appendHighlightedSegment(targetOut, newText, true, targetColor);
            return;
        }
        appendSubDiff(installedOut, oldText, prefix, oldText.length() - suffix, installedColor);
        appendSubDiff(targetOut, newText, prefix, newText.length() - suffix, targetColor);
    }

    private static void appendSubDiff(
            SpannableStringBuilder out,
            String text,
            int diffStart,
            int diffEnd,
            int highlightColor) {
        if (diffStart > 0) {
            out.append(text, 0, diffStart);
        }
        if (diffEnd > diffStart) {
            appendHighlightedSegment(out, text.substring(diffStart, diffEnd), true, highlightColor);
        }
        if (diffEnd < text.length()) {
            out.append(text, diffEnd, text.length());
        }
    }

    private static void appendHighlightedSegment(
            SpannableStringBuilder out, String text, boolean highlight, int color) {
        int start = out.length();
        out.append(text);
        if (highlight && !text.isEmpty()) {
            out.setSpan(new ForegroundColorSpan(color),
                    start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new StyleSpan(Typeface.BOLD),
                    start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static List<VersionToken> tokenizeVersionName(String versionName) {
        List<VersionToken> tokens = new ArrayList<>();
        if (TextUtils.isEmpty(versionName)) {
            return tokens;
        }
        int len = versionName.length();
        int i = 0;
        while (i < len) {
            boolean sep = isVersionSeparator(versionName.charAt(i));
            int start = i;
            i++;
            while (i < len && isVersionSeparator(versionName.charAt(i)) == sep) {
                i++;
            }
            tokens.add(new VersionToken(versionName.substring(start, i), sep));
        }
        return tokens;
    }

    private static boolean isVersionSeparator(char ch) {
        return ch == '.' || ch == '-' || ch == '_' || ch == '+' || ch == ':' || ch == '/' || Character.isWhitespace(ch);
    }

    private static final class VersionToken {
        final String text;
        final boolean isSeparator;

        VersionToken(String text, boolean isSeparator) {
            this.text = text;
            this.isSeparator = isSeparator;
        }
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

    private static void bindPendingPathRow(DetailViews views, String pendingText) {
        views.pathRow.valueView.setText(pendingText);
        setCopyTarget(views.pathRow.rowView,
                CopyTarget.unavailable("本地路径"));
    }

    private static void bindPathRow(
            DetailViews views,
            String resolvedOriginalPath,
            Uri sourceUri,
            File apkFile,
            File stagedFile) {
        if (!TextUtils.isEmpty(resolvedOriginalPath)) {
            views.pathRow.valueView.setText(resolvedOriginalPath);
            setCopyTarget(views.pathRow.rowView, CopyTarget.of("本地路径", resolvedOriginalPath));
            return;
        }
        if (apkFile != null) {
            String path = apkFile.getAbsolutePath();
            boolean isStaged = stagedFile != null
                    && (stagedFile == apkFile || path.equals(stagedFile.getAbsolutePath()));
            setCopyTarget(views.pathRow.rowView, CopyTarget.of("本地路径", path));
            if (isStaged) {
                SpannableStringBuilder sb = new SpannableStringBuilder(path);
                int noteStart = sb.length();
                sb.append("（暂存文件）");
                sb.setSpan(new ForegroundColorSpan(views.theme.labelColor),
                        noteStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                views.pathRow.valueView.setText(sb);
            } else {
                views.pathRow.valueView.setText(path);
            }
            return;
        }
        if (isContentUri(sourceUri)) {
            String uriText = sourceUri.toString();
            views.pathRow.valueView.setText(uriText);
            setCopyTarget(views.pathRow.rowView, CopyTarget.of("本地路径", uriText));
            return;
        }
        views.pathRow.valueView.setText("无本地 APK 文件");
        setCopyTarget(views.pathRow.rowView,
                CopyTarget.unavailable("本地路径"));
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

    private static void bindPendingSignatureSection(DetailViews views, String pendingText) {
        SignatureSection section = views.signatureSection;
        section.summaryView.setText(pendingText);
        section.hasExpandableDetails = false;
        setArrowIndicatorVisible(section, false);
        setCopyTarget(section.headerRowView,
                CopyTarget.unavailable("SHA-256"));
        section.apkDigestRow.rowView.setVisibility(View.GONE);
        section.installedDigestRow.rowView.setVisibility(View.GONE);
        setSignatureExpanded(views, false, false);
    }

    private static void bindSignatureSection(
            DetailViews views,
            boolean isInstalled,
            SignatureData apkSig,
            SignatureData installedSig) {
        SignatureSection section = views.signatureSection;
        List<String> apkDigests = apkSig.currentDigests;
        List<String> installedDigests = installedSig.currentDigests;
        String apkHex = apkDigests.isEmpty() ? null : TextUtils.join("\n", apkDigests);
        String installedHex = installedDigests.isEmpty() ? null : TextUtils.join("\n", installedDigests);

        if (!isInstalled) {
            if (apkHex == null) {
                section.apkDigestRow.rowView.setVisibility(View.GONE);
                section.installedDigestRow.rowView.setVisibility(View.GONE);
                applySignatureHeader(
                        views,
                        "无法读取签名",
                        false,
                        CopyTarget.unavailable("SHA-256"),
                        false);
                return;
            }
            bindSingleDigestSection(views, "已读取签名", apkHex);
            return;
        }

        if (apkHex != null && installedHex != null) {
            if (apkDigests.equals(installedDigests)) {
                bindSingleDigestSection(
                        views,
                        buildStyledStatusText(
                                "一致", "（与已安装应用相同）",
                                views.theme.primaryAccentColor, views.theme.valueColor, true),
                        apkHex);
                return;
            }
            boolean rotationCompatible =
                    !apkSig.historyDigests.isEmpty() && apkSig.historyDigests.containsAll(installedDigests);
            CharSequence summaryText = rotationCompatible
                    ? buildStyledStatusText(
                            "一致", "（证书轮换兼容）",
                            views.theme.primaryAccentColor, views.theme.valueColor, true)
                    : buildStyledStatusText(
                            "不一致（与已安装应用不同）", null,
                            views.theme.errorColor, views.theme.errorColor, true);
            int apkColor = rotationCompatible ? views.theme.valueColor : views.theme.errorColor;
            bindDigestRow(section.apkDigestRow, "待安装 SHA-256", apkHex, "待安装 SHA-256", apkColor);
            bindDigestRow(section.installedDigestRow, "已安装 SHA-256", installedHex, "已安装 SHA-256", views.theme.valueColor);
            applySignatureHeader(
                    views,
                    summaryText,
                    true,
                    CopyTarget.of("待安装 SHA-256", apkHex),
                    views.signatureExpanded);
            return;
        }

        bindOptionalDigestRow(section.apkDigestRow, "待安装 SHA-256", apkHex, views.theme);
        bindOptionalDigestRow(section.installedDigestRow, "已安装 SHA-256", installedHex, views.theme);
        CopyTarget headerCopyTarget = apkHex != null
                ? CopyTarget.of("待安装 SHA-256", apkHex)
                : (installedHex != null
                        ? CopyTarget.of("已安装 SHA-256", installedHex)
                        : CopyTarget.unavailable("SHA-256"));
        applySignatureHeader(
                views,
                buildStyledStatusText(
                        "无法比对", "（签名信息不完整）",
                        views.theme.warningColor, views.theme.labelColor, true),
                apkHex != null || installedHex != null,
                headerCopyTarget,
                views.signatureExpanded);
    }

    private static void bindSingleDigestSection(
            DetailViews views,
            CharSequence summaryText,
            String apkHex) {
        SignatureSection section = views.signatureSection;
        bindDigestRow(section.apkDigestRow, "SHA-256", apkHex, "SHA-256", views.theme.valueColor);
        section.installedDigestRow.rowView.setVisibility(View.GONE);
        applySignatureHeader(
                views,
                summaryText,
                true,
                CopyTarget.of("SHA-256", apkHex),
                views.signatureExpanded);
    }

    private static void bindOptionalDigestRow(
            DetailRow digestRow,
            String label,
            String hexValue,
            DetailTheme theme) {
        if (!TextUtils.isEmpty(hexValue)) {
            bindDigestRow(digestRow, label, hexValue, label, theme.valueColor);
            return;
        }
        digestRow.rowView.setVisibility(View.VISIBLE);
        digestRow.labelView.setText(label);
        digestRow.valueView.setText("未知");
        digestRow.valueView.setTextColor(theme.labelColor);
        setCopyTarget(digestRow.rowView, CopyTarget.unavailable(label));
    }

    private static void applySignatureHeader(
            DetailViews views,
            CharSequence summaryText,
            boolean hasExpandableDetails,
            CopyTarget headerCopyTarget,
            boolean expanded) {
        SignatureSection section = views.signatureSection;
        section.summaryView.setText(summaryText);
        section.hasExpandableDetails = hasExpandableDetails;
        setArrowIndicatorVisible(section, hasExpandableDetails);
        setCopyTarget(section.headerRowView, headerCopyTarget);
        setSignatureExpanded(views, expanded, false);
    }

    private static void bindDigestRow(
            DetailRow digestRow,
            String label,
            String hexValue,
            String copyLabel,
            int textColor) {
        digestRow.rowView.setVisibility(View.VISIBLE);
        digestRow.labelView.setText(label);
        digestRow.valueView.setText(hexValue);
        digestRow.valueView.setTextColor(textColor);
        setCopyTarget(digestRow.rowView, CopyTarget.of(copyLabel, hexValue));
    }

    private static void setArrowIndicatorVisible(SignatureSection section, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        if (section.arrowView != null) {
            section.arrowView.setVisibility(visibility);
        }
        if (section.arrowFallbackView != null) {
            section.arrowFallbackView.setVisibility(visibility);
        }
    }

    private static CharSequence buildStyledStatusText(
            String lead,
            String detail,
            int leadColor,
            int detailColor,
            boolean boldLead) {
        SpannableStringBuilder sb = new SpannableStringBuilder(lead);
        sb.setSpan(new ForegroundColorSpan(leadColor), 0, lead.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (boldLead) {
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, lead.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (!TextUtils.isEmpty(detail)) {
            int start = sb.length();
            sb.append(detail);
            sb.setSpan(new ForegroundColorSpan(detailColor), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    private static final class CopyTarget {
        final String label;
        final String value;

        private CopyTarget(String label, String value) {
            this.label = label;
            this.value = value;
        }

        static CopyTarget of(String label, String value) {
            return new CopyTarget(label, value);
        }

        static CopyTarget unavailable(String label) {
            return new CopyTarget(label, null);
        }

        boolean isCopyable() {
            return !TextUtils.isEmpty(value);
        }
    }

    private static final class DetailTheme {
        final int styleId;
        final Typeface baseTypeface;
        final float letterSpacing;
        final float labelTextSizePx;
        final float valueTextSizePx;
        final float subLabelTextSizePx;
        final float digestTextSizePx;
        final int marginMediumPx;
        final int marginSmallPx;
        final int labelGapPx;
        final int cardHorizontalPaddingPx;
        final int rowHorizontalPaddingPx;
        final int rowPaddingVerticalPx;
        final int subRowPaddingVerticalPx;
        final int dividerHeightPx;
        final int highlightCornerRadiusPx;
        final int dividerColor;
        final int labelColor;
        final int valueColor;
        final int primaryAccentColor;
        final int primaryContainerColor;
        final int installedVersionDiffColor;
        final int warningColor;
        final int errorColor;
        final int errorContainerColor;
        final int subCardBgColor;

        private DetailTheme(
                int styleId,
                Typeface baseTypeface,
                float letterSpacing,
                float labelTextSizePx,
                float valueTextSizePx,
                float subLabelTextSizePx,
                float digestTextSizePx,
                int marginMediumPx,
                int marginSmallPx,
                int labelGapPx,
                int cardHorizontalPaddingPx,
                int rowHorizontalPaddingPx,
                int rowPaddingVerticalPx,
                int subRowPaddingVerticalPx,
                int dividerHeightPx,
                int highlightCornerRadiusPx,
                int dividerColor,
                int labelColor,
                int valueColor,
                int primaryAccentColor,
                int primaryContainerColor,
                int installedVersionDiffColor,
                int warningColor,
                int errorColor,
                int errorContainerColor,
                int subCardBgColor) {
            this.styleId = styleId;
            this.baseTypeface = baseTypeface;
            this.letterSpacing = letterSpacing;
            this.labelTextSizePx = labelTextSizePx;
            this.valueTextSizePx = valueTextSizePx;
            this.subLabelTextSizePx = subLabelTextSizePx;
            this.digestTextSizePx = digestTextSizePx;
            this.marginMediumPx = marginMediumPx;
            this.marginSmallPx = marginSmallPx;
            this.labelGapPx = labelGapPx;
            this.cardHorizontalPaddingPx = cardHorizontalPaddingPx;
            this.rowHorizontalPaddingPx = rowHorizontalPaddingPx;
            this.rowPaddingVerticalPx = rowPaddingVerticalPx;
            this.subRowPaddingVerticalPx = subRowPaddingVerticalPx;
            this.dividerHeightPx = dividerHeightPx;
            this.highlightCornerRadiusPx = highlightCornerRadiusPx;
            this.dividerColor = dividerColor;
            this.labelColor = labelColor;
            this.valueColor = valueColor;
            this.primaryAccentColor = primaryAccentColor;
            this.primaryContainerColor = primaryContainerColor;
            this.installedVersionDiffColor = installedVersionDiffColor;
            this.warningColor = warningColor;
            this.errorColor = errorColor;
            this.errorContainerColor = errorContainerColor;
            this.subCardBgColor = subCardBgColor;
        }

        static DetailTheme resolve(Activity activity, String pkg, TextView refView) {
            Resources res = activity.getResources();
            int styleId = res.getIdentifier("apk_detail_info", "style", pkg);
            int dimenId = res.getIdentifier("apk_detail_info_sp", "dimen", pkg);
            float baseSizePx;
            if (refView != null) {
                baseSizePx = refView.getTextSize();
            } else if (dimenId != 0) {
                baseSizePx = res.getDimension(dimenId);
            } else {
                baseSizePx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_SP, 14f, res.getDisplayMetrics());
            }

            float labelTextSizePx = Math.max(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, res.getDisplayMetrics()),
                    baseSizePx * 0.88f);
            float subLabelTextSizePx = Math.max(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11.5f, res.getDisplayMetrics()),
                    baseSizePx * 0.84f);
            float digestTextSizePx = Math.max(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, res.getDisplayMetrics()),
                    baseSizePx * 0.88f);

            int marginMediumPx = resolveDimenAttrOrFallback(
                    activity, pkg, "marginMedium", "fd_sys_margin_medium", 16);
            int marginSmallPx = resolveDimenAttrOrFallback(
                    activity, pkg, "marginSmall", "fd_sys_margin_small", 8);
            int rowHorizontalPaddingPx = dpToPx(activity, 6);
            int cardHorizontalPaddingPx = Math.max(0, marginMediumPx - rowHorizontalPaddingPx);

            int dividerColor = resolveColorAttrOrFallback(
                    activity, pkg, "colorOutlineVariant", "fd_sys_color_outline_variant_default", 0x0D000000);
            int labelColor = resolveColorAttrOrFallback(
                    activity, pkg, "colorOnSurfaceVariant", "fd_sys_color_on_surface_variant_default", 0x993C3C43);
            int valueColor = resolveColorAttrOrFallback(
                    activity, pkg, "colorOnSurface", "fd_sys_color_on_surface_default", 0xFF000000);
            int primaryAccentColor = resolveColorAttrOrFallback(
                    activity, pkg, "colorPrimary", "fd_sys_color_primary_green", 0xFF35C44F);
            int primaryContainerColor = withFallbackAlpha(
                    resolveColorAttrOrFallback(
                            activity, pkg, "colorPrimaryContainer", "fd_sys_color_primary_container_green", 0),
                    primaryAccentColor, 0x24);
            int installedVersionDiffColor = resolveColorAttrOrFallback(
                    activity, pkg, null, "fd_sys_color_primary_blue", 0xFF206CFF);
            int warningColor = resolveColorAttrOrFallback(
                    activity, pkg, null, "warning_color", 0xFFFF7024);
            int errorColor = resolveColorAttrOrFallback(
                    activity, pkg, "colorError", "fd_sys_color_error_default", 0xFFFF0000);
            int errorContainerColor = withFallbackAlpha(
                    resolveColorAttrOrFallback(
                            activity, pkg, "colorErrorContainer", "fd_sys_color_error_container_default", 0),
                    errorColor, 0x1E);
            int subCardBgColor = resolveColorAttrOrFallback(
                    activity, pkg, null, "fd_sys_color_primary_container_default",
                    withFallbackAlpha(0, labelColor, 0x12));

            return new DetailTheme(
                    styleId,
                    refView != null ? refView.getTypeface() : null,
                    refView != null ? refView.getLetterSpacing() : 0f,
                    labelTextSizePx,
                    baseSizePx,
                    subLabelTextSizePx,
                    digestTextSizePx,
                    marginMediumPx,
                    marginSmallPx,
                    dpToPx(activity, 2),
                    cardHorizontalPaddingPx,
                    rowHorizontalPaddingPx,
                    dpToPx(activity, 11),
                    dpToPx(activity, 8),
                    Math.max(1, dpToPx(activity, 1)),
                    dpToPx(activity, 8),
                    dividerColor,
                    labelColor,
                    valueColor,
                    primaryAccentColor,
                    primaryContainerColor,
                    installedVersionDiffColor,
                    warningColor,
                    errorColor,
                    errorContainerColor,
                    subCardBgColor);
        }

        private static int withFallbackAlpha(int resolvedColor, int tintBase, int alpha) {
            if (resolvedColor != 0) {
                return resolvedColor;
            }
            return Color.argb(alpha, Color.red(tintBase), Color.green(tintBase), Color.blue(tintBase));
        }
    }

    private static final class DetailRow {
        final View rowView;
        final TextView labelView;
        final TextView valueView;

        DetailRow(View rowView, TextView labelView, TextView valueView) {
            this.rowView = rowView;
            this.labelView = labelView;
            this.valueView = valueView;
        }
    }

    private static final class SignatureSection {
        final View headerRowView;
        final TextView summaryView;
        final ImageView arrowView;
        final TextView arrowFallbackView;
        final ViewGroup digestContainer;
        final DetailRow apkDigestRow;
        final DetailRow installedDigestRow;
        boolean hasExpandableDetails;

        SignatureSection(
                View headerRowView,
                TextView summaryView,
                ImageView arrowView,
                TextView arrowFallbackView,
                ViewGroup digestContainer,
                DetailRow apkDigestRow,
                DetailRow installedDigestRow) {
            this.headerRowView = headerRowView;
            this.summaryView = summaryView;
            this.arrowView = arrowView;
            this.arrowFallbackView = arrowFallbackView;
            this.digestContainer = digestContainer;
            this.apkDigestRow = apkDigestRow;
            this.installedDigestRow = installedDigestRow;
        }
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
        final Field apkSizeView;
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
            apkSizeView = getAccessibleField(activityClass, "apkSizeView");
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
        final Activity activity;
        final View container;
        final DetailTheme theme;
        final DetailRow installedVersionRow;
        final View installedVersionDivider;
        final DetailRow targetVersionRow;
        final DetailRow abiRow;
        final SignatureSection signatureSection;
        final DetailRow pathRow;
        String boundDetailIdentity;
        boolean signatureExpanded;
        View activeHighlightView;
        Toast activeToast;
        Runnable pendingResetRunnable;

        DetailViews(
                Activity activity,
                View container,
                DetailTheme theme,
                DetailRow installedVersionRow,
                View installedVersionDivider,
                DetailRow targetVersionRow,
                DetailRow abiRow,
                SignatureSection signatureSection,
                DetailRow pathRow) {
            this.activity = activity;
            this.container = container;
            this.theme = theme;
            this.installedVersionRow = installedVersionRow;
            this.installedVersionDivider = installedVersionDivider;
            this.targetVersionRow = targetVersionRow;
            this.abiRow = abiRow;
            this.signatureSection = signatureSection;
            this.pathRow = pathRow;
        }
    }
}
