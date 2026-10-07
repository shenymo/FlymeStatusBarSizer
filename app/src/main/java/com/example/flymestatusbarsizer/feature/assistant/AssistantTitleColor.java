package com.example.flymestatusbarsizer.feature.assistant;

import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Temporarily colors the native overview heading and search bar in the global window. */
final class AssistantTitleColor implements ViewTreeObserver.OnPreDrawListener {
    private final View root;
    private final Target[] targets;
    private ViewTreeObserver observer;
    private ColorStateList tint;
    private boolean active;

    AssistantTitleColor(View decor) {
        this(decor, id(decor, "title"), id(decor, "add_widget"),
                id(decor, "search_layout"), id(decor, "search_view_text"),
                id(decor, "mc_search_icon"));
    }

    AssistantTitleColor(View decor, int title, int add, int search, int text, int icon) {
        root = decor;
        targets = new Target[]{
                target(decor, title, TextView.class),
                target(decor, add, ImageView.class),
                target(decor, search, View.class),
                target(decor, text, TextView.class),
                target(decor, icon, ImageView.class)
        };
    }

    // Also used by the lifecycle tests without depending on the assistant APK's resources.
    AssistantTitleColor(TextView title) {
        root = title;
        targets = new Target[]{new Target(() -> title)};
    }

    private static int id(View root, String name) {
        return root.getResources().getIdentifier(name, "id", AssistantProtocol.PACKAGE);
    }

    private static Target target(View root, int id, Class<? extends View> type) {
        return new Target(() -> {
            View view = id == 0 ? null : root.findViewById(id);
            return type.isInstance(view) ? view : null;
        });
    }

    void setTint(Integer color) {
        tint = color == null ? null : ColorStateList.valueOf(color);
        if (active) update();
    }

    void apply() {
        if (active || root == null) return;
        active = true;
        observer = root.getViewTreeObserver();
        observer.addOnPreDrawListener(this);
        update();
    }

    private void update() {
        if (tint == null) return;
        for (Target target : targets) target.update(tint);
    }

    @Override public boolean onPreDraw() {
        if (active) update();
        return true;
    }

    void restore() {
        if (!active) return;
        active = false;
        if (observer != null && observer.isAlive()) observer.removeOnPreDrawListener(this);
        // addView may return before attachment: Android then merges the floating observer into
        // the ViewRoot observer. Remove from that live observer too when returning to desktop.
        ViewTreeObserver current = root.getViewTreeObserver();
        if (current != observer && current.isAlive()) current.removeOnPreDrawListener(this);
        observer = null;
        for (Target target : targets) target.restore();
        // The subsequent desktop attachment lets WallpaperColorManager apply its latest state.
    }

    private static final class Target {
        private final Supplier<View> find;
        private final List<ColorOverride> colors = new ArrayList<>();
        private View view;

        Target(Supplier<View> find) { this.find = find; }

        void update(ColorStateList tint) {
            // SearchViewHolder is recycled: discover late/new views and release the old ones.
            View current = find.get();
            if (current != view) {
                restore();
                view = current;
                if (view instanceof TextView) {
                    TextView text = (TextView) view;
                    colors.add(new ColorOverride(text::getTextColors, text::setTextColor));
                    // The visible native "搜索" label is a hint, not text.
                    colors.add(new ColorOverride(text::getHintTextColors, text::setHintTextColor));
                } else if (view != null) {
                    if (view instanceof ImageView) {
                        ImageView image = (ImageView) view;
                        colors.add(new ColorOverride(image::getImageTintList, image::setImageTintList));
                    }
                    // Tint the existing drawable, preserving its alpha, shape and tint mode.
                    colors.add(new ColorOverride(view::getBackgroundTintList, view::setBackgroundTintList));
                }
            }
            for (ColorOverride color : colors) color.update(tint);
        }

        void restore() {
            for (ColorOverride color : colors) color.restore();
            colors.clear();
            view = null;
        }
    }

    private static final class ColorOverride {
        private final Supplier<ColorStateList> read;
        private final Consumer<ColorStateList> write;
        private ColorStateList nativeColors, appliedColors;

        ColorOverride(Supplier<ColorStateList> read, Consumer<ColorStateList> write) {
            this.read = read;
            this.write = write;
        }

        void update(ColorStateList tint) {
            ColorStateList current = read.get();
            // Preserve wallpaper/rebind updates for the eventual return to the desktop.
            if (current != appliedColors) nativeColors = current;
            appliedColors = tint;
            if (current != tint) write.accept(tint);
        }

        void restore() {
            if (appliedColors != null && read.get() == appliedColors) write.accept(nativeColors);
        }
    }
}
