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
                    Object value = FlymeStatusBarSizer.getFieldCompat(
                            chain.getThisObject(), "mContext");
                    Context context = value instanceof Context ? (Context) value : null;
                    int destinationPort = (Integer) chain.getArg(2);
                    if (context != null) {
                        VerificationCodeClient.recordDiagnostic(context, "短信类型检查",
                                destinationPort == -1,
                                destinationPort == -1 ? "收到普通短信"
                                        : "端口短信被跳过，端口值 " + destinationPort);
                    }
                    if (destinationPort == -1) {
                        FlymeStatusBarSizer.ImeConfigSnapshot config =
                                FlymeStatusBarSizer.loadImeConfig(null);
                        String configFailure = config == null || !config.enabled
                                ? "模块功能未启用"
                                : !ImeToolbarSpec.containsVerificationCode(
                                        config.imeControlBarButtonSlots,
                                        config.imeControlBarTextSlots)
                                                ? "工具栏未添加验证码按钮" : null;
                        if (configFailure == null && !ImeToolbarSpec.hasVisibleVerificationCode(
                                config.imeControlBarButtonSlots,
                                config.imeControlBarTextSlots,
                                config.imeControlBarExpansionEnabled)) {
                            configFailure = "验证码在文字栏，但扩展栏未开启";
                        }
                        if (configFailure != null) {
                            if (context != null) {
                                VerificationCodeClient.recordDiagnostic(context, "配置检查",
                                        false, configFailure);
                            }
                            return chain.proceed();
                        }
                        if (context != null) {
                            VerificationCodeClient.recordDiagnostic(context, "配置检查", true,
                                    "验证码按钮已启用");
                        }
                        String code = extractCode(context,
                                (byte[][]) chain.getArg(0), (String) chain.getArg(1));
                        if (code != null && context != null) {
                            VerificationCodeClient.publish(context, code);
                        }
                    }
                } catch (Throwable error) {
                    Object value = FlymeStatusBarSizer.getFieldCompat(
                            chain.getThisObject(), "mContext");
                    if (value instanceof Context) {
                        VerificationCodeClient.recordDiagnostic((Context) value,
                                "短信识别异常", false, "处理短信时发生异常，请检查模块日志");
                    }
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

    private static String extractCode(Context context, byte[][] pdus, String format) {
        if (pdus == null || format == null) {
            if (context != null) {
                VerificationCodeClient.recordDiagnostic(context, "短信解码", false,
                        "短信数据或编码格式为空");
            }
            return null;
        }
        StringBuilder body = new StringBuilder();
        int decodedParts = 0;
        for (byte[] pdu : pdus) {
            SmsMessage message = SmsMessage.createFromPdu(pdu, format);
            if (message != null && message.getMessageBody() != null) {
                body.append(message.getMessageBody());
                decodedParts++;
            }
        }
        if (context != null) {
            VerificationCodeClient.recordDiagnostic(context, "短信解码", decodedParts > 0,
                    decodedParts > 0 ? "已解码 " + decodedParts + " 段短信内容"
                            : "没有可读取的短信内容");
        }
        Matcher matcher = CODE_PATTERN.matcher(body);
        if (!matcher.find()) {
            if (context != null) {
                VerificationCodeClient.recordDiagnostic(context, "验证码匹配", false,
                        "未找到独立的 4 至 8 位数字");
            }
            return null;
        }
        if (context != null) {
            VerificationCodeClient.recordDiagnostic(context, "验证码匹配", true,
                    "找到符合规则的数字验证码");
        }
        return matcher.group();
    }
}
