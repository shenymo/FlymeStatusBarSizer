package com.example.flymestatusbarsizer.feature.assistant;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.WindowManager;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;

public final class AssistantHooks {
    private static volatile AssistantController controller;
    private static boolean installed;
    private AssistantHooks() { }

    public static void onStatusBarTintChanged(int color) {
        AssistantClient.statusBarTintChanged(color);
    }

    public static void installSystemUi(FlymeStatusBarSizer module, ClassLoader loader) {
        try { AssistantGestureHooks.install(module, loader); }
        catch (Throwable t) { warn("Cannot install assistant edge gesture", t); }
    }

    public static void installAssistant(FlymeStatusBarSizer module, ClassLoader loader) {
        try {
            Class<?> service = Class.forName(AssistantProtocol.SERVICE, false, loader);
            Class<?> component = Class.forName("com.meizu.assistant.function.MainScrollOverlayComponent", false, loader);
            Class<?> dispatcher = Class.forName("com.meizu.flyme.assistant.slidepanel.LauncherOverlayCallback", false, loader);
            Class<?> overlay = Class.forName("com.meizu.flyme.assistant.slidepanel.OverlayComponent", false, loader);
            Class<?> panel = Class.forName("com.meizu.flyme.assistant.slidepanel.SlidingPanelLayout", false, loader);
            Class<?> wrapper = Class.forName("com.meizu.flyme.assistant.slidepanel.DialogOverlayContextWrapper", false, loader);
            module.intercept(AssistantReflection.method(service, "onCreate"), chain -> {
                Object result = chain.proceed();
                if (controller == null) {
                    controller = new AssistantController((Context) chain.getThisObject(), module);
                    controller.setHooksReady(installed);
                }
                return result;
            });
            module.intercept(AssistantReflection.method(service, "onBind", Intent.class), chain -> {
                Intent intent = (Intent) chain.getArg(0);
                if (intent != null && AssistantProtocol.ACTION.equals(intent.getAction()))
                    return controller == null ? null : controller.control;
                return chain.proceed();
            });
            module.intercept(AssistantReflection.method(service, "onDestroy"), chain -> {
                if (controller != null) {
                    controller.destroy();
                    controller = null;
                }
                return chain.proceed();
            });
            module.intercept(AssistantReflection.method(component, "onCreate", Bundle.class), chain -> {
                Object result = chain.proceed();
                if (controller != null) controller.created(chain.getThisObject());
                return result;
            });
            module.intercept(AssistantReflection.method(component, "onDestroy"), chain -> {
                if (controller != null) controller.componentDestroying(chain.getThisObject());
                return chain.proceed();
            });
            module.intercept(AssistantReflection.method(dispatcher, "handleMessage", Message.class), chain -> {
                if (controller != null && controller.interceptLauncherMessage(chain.getThisObject(), (Message) chain.getArg(0)))
                    return true;
                Object result = chain.proceed();
                int what = ((Message) chain.getArg(0)).what;
                if (controller != null && what >= 3 && what <= 10) controller.publishState();
                return result;
            });
            module.intercept(AssistantReflection.method(overlay, "onWindowAttributesChanged", WindowManager.LayoutParams.class), chain -> {
                AssistantWindowSession current = controller == null ? null : controller.session;
                if (current != null && !current.restoring && current.component == chain.getThisObject()) {
                    try { current.attributesChanged((WindowManager.LayoutParams) chain.getArg(0)); }
                    catch (Throwable t) { warn("Cannot update assistant window", t); controller.restore(true); }
                    return null;
                }
                return chain.proceed();
            });
            module.intercept(AssistantReflection.method(panel, "onPanelClosed"), chain -> {
                Object result = chain.proceed();
                if (controller != null) controller.panelClosed(chain.getThisObject());
                return result;
            });
            module.intercept(AssistantReflection.method(panel, "onPanelOpened"), chain -> {
                Object result = chain.proceed();
                AssistantWindowSession current = activePanel(chain.getThisObject());
                if (current != null) current.motion.opened();
                if (controller != null) controller.publishState();
                return result;
            });
            for (Method method : new Method[]{
                    AssistantReflection.method(panel, "setPanelX", int.class),
                    AssistantReflection.method(panel, "onLayout", boolean.class,
                            int.class, int.class, int.class, int.class)}) {
                module.intercept(method, chain -> {
                    Object result = chain.proceed();
                    AssistantWindowSession current = activePanel(chain.getThisObject());
                    if (current != null) current.motion.positionChanged();
                    return result;
                });
            }
            for (Class<?> argument : new Class<?>[]{int.class, float.class}) {
                module.intercept(AssistantReflection.method(panel, "closePanel", argument), chain -> {
                    AssistantWindowSession current = activePanel(chain.getThisObject());
                    if (current != null) current.motion.prepareClose();
                    return chain.proceed();
                });
            }
            for (String name : new String[]{"onInterceptTouchEvent", "onTouchEvent"}) {
                boolean intercept = name.equals("onInterceptTouchEvent");
                module.intercept(AssistantReflection.method(panel, name, MotionEvent.class), chain -> {
                    AssistantWindowSession current = activePanel(chain.getThisObject());
                    if (current == null) return chain.proceed();
                    try {
                        MotionEvent event = (MotionEvent) chain.getArg(0);
                        return intercept ? current.motion.intercept(event) : current.motion.touch(event);
                    } catch (Throwable t) {
                        warn("Cannot handle assistant panel swipe", t);
                        controller.restore(true);
                        return true;
                    }
                });
            }
            for (Method method : wrapper.getDeclaredMethods()) {
                if (!"startActivity".equals(method.getName())) continue;
                method.setAccessible(true);
                module.intercept(method, chain -> {
                    AssistantWindowSession current = controller == null ? null : controller.session;
                    if (current != null && current.component == chain.getThisObject()) controller.restore(true);
                    return chain.proceed();
                });
            }
            installed = true;
            if (controller != null) controller.setHooksReady(true);
        } catch (Throwable t) { warn("Cannot install assistant window hooks", t); }
    }

    public static void refresh() {
        AssistantClient.refresh();
        if (controller != null) controller.refresh();
    }

    static void warn(String message, Throwable t) { Log.w("FlymeAssistantGesture", message, t); }

    static AssistantWindowSession currentSession() {
        AssistantController value = controller;
        return value == null ? null : value.session;
    }

    private static AssistantWindowSession activePanel(Object panel) {
        AssistantWindowSession current = currentSession();
        return current != null && !current.restoring && current.panel == panel ? current : null;
    }
}
