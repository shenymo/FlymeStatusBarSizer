package com.example.flymestatusbarsizer.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.example.flymestatusbarsizer.MainActivity;
import com.example.flymestatusbarsizer.R;
import com.example.flymestatusbarsizer.feature.battery.BatteryIconPack;
import com.example.flymestatusbarsizer.feature.statusbar.RingerVectorXml;
import com.example.flymestatusbarsizer.util.OnlineIconLibraryClient;

import java.util.List;

public final class OnlineIconLibraryDialog {
    public record UploadData(String name, String payload) {
    }

    public interface UploadProvider {
        List<UploadData> choices() throws Exception;
    }

    public interface DownloadHandler {
        String download(OnlineIconLibraryClient.RemoteIcon icon) throws Exception;
    }

    private OnlineIconLibraryDialog() {
    }

    public static void addEntry(MainActivity activity, LinearLayout parent, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.END);
        row.setPadding(activity.dp(16), activity.dp(4), activity.dp(16), activity.dp(8));
        TextView entry = button(activity, "在线图标库", action);
        entry.setPadding(activity.dp(16), 0, activity.dp(16), 0);
        row.addView(entry, new LinearLayout.LayoutParams(-2, activity.dp(44)));
        parent.addView(row);
    }

    public static void show(MainActivity activity, String type, String title,
                            UploadProvider uploadProvider, DownloadHandler downloadHandler) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);
        root.setBackgroundColor(activity.surfaceColor());

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = iconButton(activity, R.drawable.ic_settings_back, "返回", dialog::dismiss);
        header.addView(back, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(56)));
        TextView heading = text(activity, title, 20);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setSingleLine(true);
        heading.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(heading, new LinearLayout.LayoutParams(0, activity.dp(56), 1));
        TextView upload = button(activity, "上传", () -> { });
        header.addView(upload, new LinearLayout.LayoutParams(activity.dp(64), activity.dp(44)));
        ImageView refresh = iconButton(activity, R.drawable.ic_refresh_24px, "刷新", () -> { });
        header.addView(refresh, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
        root.addView(header);

        LinearLayout filters = new LinearLayout(activity);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(12));
        LinearLayout tabs = new LinearLayout(activity);
        tabs.setPadding(activity.dp(4), activity.dp(4), activity.dp(4), activity.dp(4));
        tabs.setBackground(activity.roundRect(activity.surfaceSoftColor(), 12));
        TextView popular = text(activity, "热门", 14);
        TextView latest = text(activity, "最新", 14);
        for (TextView tab : new TextView[]{popular, latest}) {
            tab.setGravity(Gravity.CENTER);
            tabs.addView(tab, new LinearLayout.LayoutParams(activity.dp(64), activity.dp(36)));
        }
        filters.addView(tabs);
        TextView count = text(activity, "", 12);
        count.setTextColor(activity.subtextColor());
        count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        filters.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(filters);

        FrameLayout body = new FrameLayout(activity);
        ScrollView scroll = new ScrollView(activity);
        scroll.setClipToPadding(false);
        scroll.setPadding(activity.dp(16), 0, activity.dp(16), activity.dp(16));
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout state = new LinearLayout(activity);
        state.setOrientation(LinearLayout.VERTICAL);
        state.setGravity(Gravity.CENTER);
        state.setPadding(activity.dp(32), activity.dp(24), activity.dp(32), activity.dp(24));
        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminateTintList(ColorStateList.valueOf(activity.primaryColor()));
        state.addView(progress, new LinearLayout.LayoutParams(activity.dp(32), activity.dp(32)));
        TextView message = text(activity, "正在加载…", 15);
        message.setGravity(Gravity.CENTER);
        message.setTextColor(activity.subtextColor());
        message.setMaxLines(4);
        message.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(-1, -2);
        messageParams.setMargins(0, activity.dp(16), 0, activity.dp(16));
        state.addView(message, messageParams);
        TextView retry = button(activity, "重试", () -> { });
        state.addView(retry, new LinearLayout.LayoutParams(activity.dp(96), activity.dp(44)));
        body.addView(state, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));

        boolean[] popularSort = {true};
        Runnable updateTabs = () -> {
            popular.setSelected(popularSort[0]);
            latest.setSelected(!popularSort[0]);
            popular.setTextColor(popularSort[0] ? activity.primaryColor() : activity.subtextColor());
            latest.setTextColor(popularSort[0] ? activity.subtextColor() : activity.primaryColor());
            popular.setBackground(activity.roundRect(popularSort[0]
                    ? activity.primaryContainerColor() : Color.TRANSPARENT, 8));
            latest.setBackground(activity.roundRect(popularSort[0]
                    ? Color.TRANSPARENT : activity.primaryContainerColor(), 8));
        };
        updateTabs.run();
        Runnable[] load = new Runnable[1];
        load[0] = () -> {
            if (!dialog.isShowing()) return;
            refresh.setEnabled(false);
            popular.setEnabled(false);
            latest.setEnabled(false);
            count.setText("");
            scroll.setVisibility(View.GONE);
            state.setVisibility(View.VISIBLE);
            progress.setVisibility(View.VISIBLE);
            retry.setVisibility(View.GONE);
            message.setText("正在加载…");
            String sort = popularSort[0] ? "popular" : "latest";
            new Thread(() -> {
                try {
                    List<OnlineIconLibraryClient.RemoteIcon> items =
                            OnlineIconLibraryClient.list(activity, type, sort);
                    activity.runOnUiThread(() -> {
                        if (!dialog.isShowing()) return;
                        refresh.setEnabled(true);
                        popular.setEnabled(true);
                        latest.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        count.setText("已加载 " + items.size() + " 个");
                        message.setText("暂无图标");
                        state.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                        scroll.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
                        render(activity, list, items, downloadHandler, load[0]);
                    });
                } catch (Exception error) {
                    activity.runOnUiThread(() -> {
                        if (!dialog.isShowing()) return;
                        refresh.setEnabled(true);
                        popular.setEnabled(true);
                        latest.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        message.setText("加载失败\n" + error.getMessage());
                        retry.setVisibility(View.VISIBLE);
                    });
                }
            }, "online-icon-library").start();
        };
        activity.setTapClickListener(popular, v -> {
            popularSort[0] = true;
            updateTabs.run();
            load[0].run();
        });
        activity.setTapClickListener(latest, v -> {
            popularSort[0] = false;
            updateTabs.run();
            load[0].run();
        });
        activity.setTapClickListener(refresh, v -> load[0].run());
        activity.setTapClickListener(retry, v -> load[0].run());
        activity.setTapClickListener(upload, v -> {
            List<UploadData> choices;
            try {
                choices = uploadProvider.choices();
            } catch (Exception error) {
                activity.showToast(error.getMessage());
                return;
            }
            if (choices.isEmpty()) {
                activity.showToast("没有可上传的自定义图标");
                return;
            }
            String[] names = choices.stream().map(UploadData::name).toArray(String[]::new);
            int[] selected = {-1};
            AlertDialog picker = new AlertDialog.Builder(activity).setTitle("选择要上传的图标")
                    .setSingleChoiceItems(names, -1, (d, which) -> {
                        selected[0] = which;
                        ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    })
                    .setNegativeButton("取消", null).setPositiveButton("上传", null).create();
            picker.setOnShowListener(d -> {
                activity.styleDialog(picker);
                activity.attachDialogButtonHaptics(picker);
                picker.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(buttonView -> {
                    UploadData data = choices.get(selected[0]);
                    picker.dismiss();
                    upload.setEnabled(false);
                    upload.setAlpha(.5f);
                    upload.setText("上传中…");
                    new Thread(() -> {
                        try {
                            OnlineIconLibraryClient.upload(activity, type, data.name(), data.payload());
                            activity.runOnUiThread(() -> {
                                upload.setEnabled(true);
                                upload.setAlpha(1f);
                                upload.setText("上传");
                                activity.showToast("已上传到在线图标库");
                                load[0].run();
                            });
                        } catch (Exception error) {
                            activity.runOnUiThread(() -> {
                                upload.setEnabled(true);
                                upload.setAlpha(1f);
                                upload.setText("上传");
                                activity.showToast(error.getMessage());
                            });
                        }
                    }, "online-icon-upload").start();
                });
            });
            picker.show();
        });
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        window.setBackgroundDrawable(new ColorDrawable(activity.surfaceColor()));
        window.setLayout(-1, -1);
        load[0].run();
    }

    private static void render(MainActivity activity, LinearLayout list,
                               List<OnlineIconLibraryClient.RemoteIcon> items,
                               DownloadHandler downloadHandler, Runnable reload) {
        list.removeAllViews();
        for (OnlineIconLibraryClient.RemoteIcon item : items) {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(activity.dp(12), activity.dp(12), activity.dp(8), activity.dp(8));
            card.setBackground(activity.roundRect(activity.surfaceSoftColor(), 12));
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            View preview = preview(activity, item);
            row.addView(preview, new LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)));
            TextView name = text(activity, item.name(), 15);
            name.setTypeface(null, Typeface.BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(activity.dp(12), 0, activity.dp(8), 0);
            name.setTooltipText(item.name());
            row.addView(name, new LinearLayout.LayoutParams(0, activity.dp(48), 1));
            ImageView more = iconButton(activity, R.drawable.ic_settings_more,
                    "管理上传的图标", () -> { });
            more.setVisibility(item.owned() ? View.VISIBLE : View.INVISIBLE);
            row.addView(more, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)));
            activity.setTapClickListener(more, v -> {
                PopupMenu menu = new PopupMenu(activity, more);
                menu.getMenu().add("删除上传图标");
                menu.setOnMenuItemClickListener(menuItem -> {
                    AlertDialog confirm = new AlertDialog.Builder(activity)
                            .setTitle("删除在线图标？")
                            .setMessage("删除后其他用户将无法下载。")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("删除", (d, which) -> {
                                more.setEnabled(false);
                                new Thread(() -> {
                                    try {
                                        OnlineIconLibraryClient.delete(activity, item.id());
                                        activity.runOnUiThread(() -> {
                                            activity.showToast("已删除");
                                            reload.run();
                                        });
                                    } catch (Exception error) {
                                        activity.runOnUiThread(() -> {
                                            more.setEnabled(true);
                                            activity.showToast(error.getMessage());
                                        });
                                    }
                                }, "online-icon-delete").start();
                            }).create();
                    confirm.show();
                    activity.styleDialog(confirm);
                    activity.attachDialogButtonHaptics(confirm);
                    return true;
                });
                menu.show();
            });
            card.addView(row);

            LinearLayout actions = new LinearLayout(activity);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            TextView meta = text(activity, "♥ " + item.likes() + "  ·  ↓ " + item.downloadCount(), 12);
            meta.setTextColor(activity.subtextColor());
            meta.setSingleLine(true);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            actions.addView(meta, new LinearLayout.LayoutParams(0, activity.dp(44), 1));
            boolean[] liked = {item.liked()};
            int[] totals = {item.likes(), item.downloadCount()};
            ImageView like = iconButton(activity,
                    item.liked() ? R.drawable.ic_favorite_24px : R.drawable.ic_favorite_border_24px,
                    item.liked() ? "取消点赞" : "点赞", () -> { });
            like.setImageTintList(ColorStateList.valueOf(item.liked()
                    ? activity.primaryColor() : activity.subtextColor()));
            actions.addView(like, new LinearLayout.LayoutParams(activity.dp(44), activity.dp(44)));
            TextView download = button(activity, "下载", () -> { });
            download.setBackground(activity.roundRect(activity.primaryContainerColor(), 8));
            LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(activity.dp(64), activity.dp(40));
            downloadParams.setMargins(activity.dp(4), 0, 0, 0);
            actions.addView(download, downloadParams);
            activity.setTapClickListener(like, v -> {
                like.setEnabled(false);
                like.setAlpha(.5f);
                new Thread(() -> {
                    try {
                        OnlineIconLibraryClient.RemoteIcon updated =
                                OnlineIconLibraryClient.like(activity, item.id(), !liked[0]);
                        activity.runOnUiThread(() -> {
                            like.setEnabled(true);
                            like.setAlpha(1f);
                            liked[0] = updated.liked();
                            totals[0] = updated.likes();
                            like.setImageResource(updated.liked()
                                    ? R.drawable.ic_favorite_24px : R.drawable.ic_favorite_border_24px);
                            like.setImageTintList(ColorStateList.valueOf(updated.liked()
                                    ? activity.primaryColor() : activity.subtextColor()));
                            like.setContentDescription(updated.liked() ? "取消点赞" : "点赞");
                            like.setTooltipText(updated.liked() ? "取消点赞" : "点赞");
                            meta.setText("♥ " + totals[0] + "  ·  ↓ " + totals[1]);
                        });
                    } catch (Exception error) {
                        activity.runOnUiThread(() -> {
                            like.setEnabled(true);
                            like.setAlpha(1f);
                            activity.showToast(error.getMessage());
                        });
                    }
                }, "online-icon-like").start();
            });
            activity.setTapClickListener(download, v -> {
                try {
                    String message = downloadHandler.download(item);
                    activity.showToast(message);
                } catch (Exception error) {
                    activity.showToast(error.getMessage());
                    return;
                }
                download.setEnabled(false);
                new Thread(() -> {
                    try {
                        int total = OnlineIconLibraryClient.recordDownload(activity, item.id());
                        activity.runOnUiThread(() -> {
                            download.setEnabled(true);
                            totals[1] = total;
                            meta.setText("♥ " + totals[0] + "  ·  ↓ " + totals[1]);
                        });
                    } catch (Exception error) {
                        activity.runOnUiThread(() -> {
                            download.setEnabled(true);
                            activity.showToast("已下载，下载量暂未同步");
                        });
                    }
                }, "online-icon-download-count").start();
            });
            card.addView(actions);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.bottomMargin = activity.dp(8);
            list.addView(card, params);
        }
    }

    private static View preview(MainActivity activity, OnlineIconLibraryClient.RemoteIcon item) {
        if (OnlineIconLibraryClient.TYPE_BATTERY.equals(item.type())) {
            try {
                BatteryIconLibraryEditor.PreviewView view =
                        new BatteryIconLibraryEditor.PreviewView(activity, false, false);
                view.pack = BatteryIconPack.parse(item.payload());
                return view;
            } catch (IllegalArgumentException ignored) {
                return text(activity, "无效", 12);
            }
        }
        ImageView image = new ImageView(activity);
        try {
            RingerVectorXml vector = RingerVectorXml.parse(item.payload());
            Drawable drawable = vector.newDrawable(activity);
            drawable.setTint(activity.textColor());
            image.setImageDrawable(drawable);
        } catch (IllegalArgumentException ignored) {
            image.setImageDrawable(activity.getDrawable(com.example.flymestatusbarsizer.R.drawable.ic_settings_tune));
        }
        return image;
    }

    private static TextView text(MainActivity activity, String value, int size) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(activity.textColor());
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private static ImageView iconButton(MainActivity activity, int resource, String description,
                                        Runnable action) {
        ImageView view = new ImageView(activity);
        view.setImageResource(resource);
        view.setImageTintList(ColorStateList.valueOf(activity.textColor()));
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setContentDescription(description);
        view.setTooltipText(description);
        activity.setTapClickListener(view, v -> action.run());
        return view;
    }

    private static TextView button(MainActivity activity, String value, Runnable action) {
        TextView view = text(activity, value, 13);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(activity.primaryColor());
        view.setBackground(activity.outlinedRect(Color.TRANSPARENT, activity.primaryColor(), 1, 8));
        activity.setTapClickListener(view, v -> action.run());
        return view;
    }
}
