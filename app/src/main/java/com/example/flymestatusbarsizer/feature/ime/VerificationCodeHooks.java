package com.example.flymestatusbarsizer.feature.ime;

import android.content.Context;
import android.telephony.SmsMessage;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;
import com.example.flymestatusbarsizer.config.ModuleConfig;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VerificationCodeHooks {
    private static final Pattern CODE_PATTERN = Pattern.compile("(?<![0-9])[0-9]{4,8}(?![0-9])");
    private static boolean installed;

    private VerificationCodeHooks() {
    }

    public static void install(FlymeStatusBarSizer module, ClassLoader loader) {
        if (module == null || loader == null || installed) {
            return;
        }
        try {
            Class<?> handler = Class.forName(
                    "com.android.internal.telephony.InboundSmsHandler", false, loader);
            Method dispatch = findDispatchMethod(handler);
            if (dispatch == null) {
                throw new NoSuchMethodException("dispatchSmsDeliveryIntent");
            }
            dispatch.setAccessible(true);
            module.intercept(dispatch, chain -> {
                try {
                    if ((Integer) chain.getArg(2) == -1 && shouldCapture()) {
                        String code = extractCode(
                                (byte[][]) chain.getArg(0), (String) chain.getArg(1));
                        if (code != null) {
                            Object value = FlymeStatusBarSizer.getFieldCompat(
                                    chain.getThisObject(), "mContext");
                            if (value instanceof Context) {
                                VerificationCodeClient.publish((Context) value, code);
                            }
                        }
                    }
                } catch (Throwable error) {
                    FlymeStatusBarSizer.logImeWarning(
                            "Failed to capture incoming SMS verification code", error);
                }
                return chain.proceed();
            });
            installed = true;
        } catch (Throwable error) {
            FlymeStatusBarSizer.logImeWarning(
                    "Failed to hook inbound SMS dispatch for verification codes", error);
        }
    }

    private static Method findDispatchMethod(Class<?> handler) {
        for (Method method : handler.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!"dispatchSmsDeliveryIntent".equals(method.getName())
                    || types.length != 7
                    || types[0] != byte[][].class
                    || types[1] != String.class
                    || types[2] != int.class
                    || types[4] != boolean.class
                    || types[5] != int.class
                    || types[6] != long.class) {
                continue;
            }
            return method;
        }
        return null;
    }

    private static boolean shouldCapture() {
        FlymeStatusBarSizer.ImeConfigSnapshot config =
                FlymeStatusBarSizer.loadImeConfig(null);
        return config != null && config.enabled
                && ImeToolbarSpec.resolveButtonSlots(config).contains("verification_code");
    }

    private static String extractCode(byte[][] pdus, String format) {
        if (pdus == null || format == null) {
            return null;
        }
        StringBuilder body = new StringBuilder();
        for (byte[] pdu : pdus) {
            SmsMessage message = SmsMessage.createFromPdu(pdu, format);
            if (message != null && message.getMessageBody() != null) {
                body.append(message.getMessageBody());
            }
        }
        Matcher matcher = CODE_PATTERN.matcher(body);
        return matcher.find() ? matcher.group() : null;
    }
}
