package com.example.flymestatusbarsizer.feature.ime;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.telephony.SmsMessage;

import com.example.flymestatusbarsizer.FlymeStatusBarSizer;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VerificationCodeHooks {
    private static final Pattern CODE_PATTERN = Pattern.compile("(?<![0-9])[0-9]{4,8}(?![0-9])");
    private static final Pattern CODE_KEYWORD_PATTERN = Pattern.compile(
            "(?:验\\s*证|动\\s*态|校\\s*验)[\\s、，,。:：;；\\-—_]*码");
    private static final int KEYWORD_CODE_WINDOW = 32;
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
                    Object value = FlymeStatusBarSizer.getFieldCompat(
                            chain.getThisObject(), "mContext");
                    Context context = value instanceof Context ? (Context) value : null;
                    int destinationPort = (Integer) chain.getArg(2);
                    if (destinationPort == -1) {
                        FlymeStatusBarSizer.ImeConfigSnapshot config =
                                FlymeStatusBarSizer.loadImeConfig(null);
                        if (config == null || !config.enabled
                                || !config.imeVerificationCodeAutoCopyEnabled) {
                            return chain.proceed();
                        }
                        String code = extractCode(
                                (byte[][]) chain.getArg(0), (String) chain.getArg(1));
                        if (code != null && context != null) {
                            ClipboardManager clipboard = (ClipboardManager)
                                    context.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (clipboard != null) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("验证码", code));
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
        String bodyText = body.toString();
        String code = null;
        Matcher keywordMatcher = CODE_KEYWORD_PATTERN.matcher(bodyText);
        while (keywordMatcher.find() && code == null) {
            int afterEnd = Math.min(bodyText.length(), keywordMatcher.end() + KEYWORD_CODE_WINDOW);
            Matcher afterMatcher = CODE_PATTERN.matcher(bodyText.substring(keywordMatcher.end(), afterEnd));
            if (afterMatcher.find()) {
                code = afterMatcher.group();
                break;
            }
            int beforeStart = Math.max(0, keywordMatcher.start() - KEYWORD_CODE_WINDOW);
            Matcher beforeMatcher = CODE_PATTERN.matcher(bodyText.substring(beforeStart, keywordMatcher.start()));
            while (beforeMatcher.find()) {
                code = beforeMatcher.group();
            }
        }
        if (code == null) {
            Matcher matcher = CODE_PATTERN.matcher(bodyText);
            if (matcher.find()) {
                code = matcher.group();
            }
        }
        if (code == null) {
            return null;
        }
        return code;
    }
}
