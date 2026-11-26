package com.example.flymestatusbarsizer.feature.share;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.util.Log;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/** Flyme Gallery embeds its own share sheet instead of launching IntentResolver. */
public final class GalleryShareTargetsHooks {
    public static final String PACKAGE = "com.meizu.media.gallery";
    private static final String TAG = "FlymeStatusBarSizer";
    private static final String PREFIX = "com.meizu.galleryshare.";

    private GalleryShareTargetsHooks() {}

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> adapter = Class.forName(PREFIX + "GalleryShareAppAdapter", false, loader);
            Class<?> listener = Class.forName(PREFIX + "GalleryDisplayShareAppWidget$OnViewClickListener",
                    false, loader);
            Method click = null;
            for (Method method : listener.getDeclaredMethods()) {
                if (method.getName().equals("onClick") && method.getParameterCount() == 3) {
                    if (click != null) throw new NoSuchMethodException("Ambiguous Gallery share callback");
                    click = method;
                }
            }
            if (click == null) throw new NoSuchMethodException("Gallery share callback");
            Binding binding = new Binding(adapter, click.getParameterTypes()[0]);
            module.intercept(binding.rebuild, chain -> {
                Object[] args = chain.getArgs().toArray();
                try {
                    if (args[0] instanceof List<?>) {
                        args[0] = binding.apply(ModuleConfig.load(null), chain.getThisObject(), (List<?>) args[0]);
                    }
                } catch (Throwable error) {
                    Log.w(TAG, "Gallery share rules failed; using original targets", error);
                }
                return chain.proceed(args);
            });
            // setData, the synthetic rebuild accessor, and the More holder can inline rebuild.
            deoptimizeCallers(module, adapter);
            for (Class<?> holder : adapter.getDeclaredClasses()) deoptimizeCallers(module, holder);
            Log.i(TAG, "Gallery share rules installed: " + binding.rebuild);
        } catch (Throwable error) {
            Log.w(TAG, "Gallery share hooks unavailable on this version", error);
        }
    }

    private static void deoptimizeCallers(FlymeStatusBarSizer module, Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) continue;
            try {
                module.deoptimize(method);
            } catch (Throwable error) {
                Log.w(TAG, "Cannot deoptimize Gallery share caller " + method.getName(), error);
            }
        }
    }

    // Verified against Gallery 12.7.3: rebuild=x(List), context=d, item ResolveInfo=a,
    // item Intent=b. Resolve obfuscated members by their unique shape, failing closed
    // on ambiguity instead of depending on JADX's generated field aliases.
    static final class Binding {
        private final ShareContentType.Cache contentTypes = new ShareContentType.Cache();
        final Method rebuild;
        private final Field context;
        private final Field resolveInfo;
        private final Field intent;

        Binding(Class<?> adapter, Class<?> item) throws ReflectiveOperationException {
            Method found = null;
            for (Method method : adapter.getDeclaredMethods()) {
                if (!Modifier.isPrivate(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class || method.getParameterCount() != 1
                        || method.getParameterTypes()[0] != List.class) continue;
                if (found != null) throw new NoSuchMethodException("Ambiguous Gallery list rebuild");
                found = method;
            }
            if (found == null) throw new NoSuchMethodException("Gallery list rebuild");
            rebuild = found;
            rebuild.setAccessible(true);
            context = uniqueField(adapter, Context.class);
            resolveInfo = uniqueField(item, ResolveInfo.class);
            intent = uniqueField(item, Intent.class);
        }

        List<?> apply(ModuleConfig config, Object adapter, List<?> source) throws IllegalAccessException {
            if (!config.enabled || !config.shareTargetsEnabled || source.isEmpty()) return source;
            // Every target carries the query Intent for this selection. The hosting Activity
            // is launched with internal album extras and does not carry the share MIME.
            ShareTargetRules rules = config.shareTargetRules;
            Intent payload = (Intent) intent.get(source.get(0));
            ShareContentType type = ShareContentType.resolve((Context) context.get(adapter), payload, contentTypes);
            rules = config.shareTargetProfiles.rulesFor(type, rules);
            if (rules.isEmpty()) return source;
            // Keep the adapter's full source list intact: More/repository refreshes rebuild
            // it again, and removing a rule must be able to restore previously hidden items.
            return rules.apply(source, this::componentOf);
        }

        private String componentOf(Object item) {
            try {
                ResolveInfo info = (ResolveInfo) resolveInfo.get(item);
                ActivityInfo activity = info == null ? null : info.activityInfo;
                return activity == null ? "" : activity.packageName + "/" + activity.name;
            } catch (IllegalAccessException error) {
                throw new IllegalStateException(error);
            }
        }

        private static Field uniqueField(Class<?> owner, Class<?> type) throws NoSuchFieldException {
            Field found = null;
            for (Field field : owner.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers()) || field.getType() != type) continue;
                if (found != null) throw new NoSuchFieldException("Ambiguous " + type.getName() + " in " + owner.getName());
                found = field;
            }
            if (found == null) throw new NoSuchFieldException(type.getName() + " in " + owner.getName());
            found.setAccessible(true);
            return found;
        }
    }
}
