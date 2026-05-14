package com.example.flymestatusbarsizer.feature.clock;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

final class ClockDetailLunarDateFormatter {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    private static final String[] CHINESE_NUMBER = {
            "一", "二", "三", "四", "五", "六", "七", "八", "九", "十", "十一", "十二"
    };
    private static final String[] CHINESE_MONTH_NUMBER = {
            "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
    };
    private static final int[] FALLBACK_LUNAR_INFO = {
            701770, 8697535, 306771, 677704, 5580477, 861776, 890180, 4631225, 354893,
            634178, 2404022, 306762, 6966718, 675154, 861510, 6116026, 742478, 879171,
            2714935, 613195, 7642049, 300884, 674632, 5973436, 435536, 447557, 4905656,
            177741, 612162, 2398135, 300874, 6703934, 870993, 959814, 5690554, 372046,
            177732, 3749688, 601675, 8165055, 824659, 870984, 7185723, 742735, 354885,
            4894137, 154957, 601410, 2921910, 693578, 8080061, 445009, 742726, 5593787,
            318030, 678723, 3484600, 338764, 9082175, 955730, 436808, 7001404, 701775,
            308805, 4871993, 677709, 337474, 4100917, 890185, 7711422, 354897, 617798,
            5549755, 306511, 675139, 5056183, 861515, 9261759, 742482, 748103, 6909244,
            613200, 301893, 4869049, 674637, 11216322, 435540, 447561, 7002685, 702033,
            612166, 5543867, 300879, 412484, 3581239, 959818, 8827583, 371795, 702023,
            5846716, 601680, 824901, 5065400, 870988, 894273, 2468534, 354889, 8039869,
            154962, 601415, 6067642, 693582, 739907, 4937015, 709962, 9788095, 309843,
            678728, 6630332, 338768, 693061, 4672185, 436812, 709953, 2415286, 308810,
            6969149, 675409, 861766, 6198074, 873293, 371267, 3585335, 617803, 11841215,
            306515, 675144, 7153084, 861519, 873028, 6138424, 744012, 355649, 2403766,
            301898, 8014782, 674641, 697670, 5984954, 447054, 711234, 3496759, 603979,
            8689601, 300883, 412488, 6726972, 959823, 436804, 4896312, 699980, 601666,
            3970869, 824905, 8211133, 870993, 894277, 5614266, 354894, 683331, 4533943,
            339275, 9082303, 693587, 739911, 7034171, 709967, 350789, 4873528, 678732,
            338754, 3838902, 430921, 7809469, 436817, 709958, 5561018, 308814, 677699,
            4532024, 861770, 9343806, 873042, 895559, 6731067, 355663, 306757, 4869817,
            675148, 857409, 2986677
    };
    private final Object reflectionLock = new Object();
    private final ClassLoader preferredClassLoader;
    private boolean reflectionResolved;
    private Method lunarDateUtilsSolarToLunarMethod;
    private Method lunarDateUtilsGetLunarYearMethod;
    private Method lunarDateUtilsGetLunarMonthAndDayMethod;
    private Method lunarCalendarSolarToLunarMethod;

    ClockDetailLunarDateFormatter(ClassLoader preferredClassLoader) {
        this.preferredClassLoader = preferredClassLoader;
    }

    String format(long nowMillis, TimeZone timeZone, Locale locale) {
        Calendar calendar = Calendar.getInstance(
                timeZone != null ? timeZone : TimeZone.getDefault(),
                locale != null ? locale : Locale.getDefault());
        calendar.setTimeInMillis(nowMillis);
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String reflected = formatWithReflection(year, month, day);
        if (!reflected.isEmpty()) {
            return reflected;
        }
        return formatWithFallback(year, month, day);
    }

    private String formatWithReflection(int year, int month, int day) {
        resolveReflectionMethods();
        if (lunarDateUtilsSolarToLunarMethod != null
                && lunarDateUtilsGetLunarYearMethod != null
                && lunarDateUtilsGetLunarMonthAndDayMethod != null) {
            try {
                Object lunarValue = lunarDateUtilsSolarToLunarMethod.invoke(null, year, month, day);
                if (lunarValue instanceof int[]) {
                    int[] lunarDate = (int[]) lunarValue;
                    String lunarYear = safeString(
                            lunarDateUtilsGetLunarYearMethod.invoke(null, lunarDate[0]));
                    String lunarMonthAndDay = safeString(
                            lunarDateUtilsGetLunarMonthAndDayMethod.invoke(null, lunarDate));
                    String merged = (lunarYear + lunarMonthAndDay).trim();
                    if (!merged.isEmpty()) {
                        return merged;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        if (lunarCalendarSolarToLunarMethod != null) {
            try {
                Object lunarValue = lunarCalendarSolarToLunarMethod.invoke(null, year, month, day);
                if (lunarValue instanceof int[]) {
                    return buildLunarText((int[]) lunarValue);
                }
            } catch (Throwable ignored) {
            }
        }
        return "";
    }

    private void resolveReflectionMethods() {
        synchronized (reflectionLock) {
            if (reflectionResolved) {
                return;
            }
            ClassLoader loader = preferredClassLoader;
            if (loader == null) {
                loader = ClockDetailLunarDateFormatter.class.getClassLoader();
            }
            if (loader == null) {
                loader = Thread.currentThread().getContextClassLoader();
            }
            if (loader != null) {
                try {
                    Class<?> lunarDateUtilsClass = Class.forName(
                            "com.flyme.keyguard.clock.LunarDateUtils",
                            false,
                            loader);
                    lunarDateUtilsSolarToLunarMethod = lunarDateUtilsClass.getDeclaredMethod(
                            "solarToLunar",
                            int.class,
                            int.class,
                            int.class);
                    lunarDateUtilsGetLunarYearMethod = lunarDateUtilsClass.getDeclaredMethod(
                            "getLunarYear",
                            int.class);
                    lunarDateUtilsGetLunarMonthAndDayMethod = lunarDateUtilsClass.getDeclaredMethod(
                            "getLunarMonthAndDay",
                            int[].class);
                } catch (Throwable ignored) {
                    lunarDateUtilsSolarToLunarMethod = null;
                    lunarDateUtilsGetLunarYearMethod = null;
                    lunarDateUtilsGetLunarMonthAndDayMethod = null;
                }
                try {
                    Class<?> lunarCalendarClass = Class.forName(
                            "com.meizu.common.util.LunarCalendar",
                            false,
                            loader);
                    lunarCalendarSolarToLunarMethod = lunarCalendarClass.getDeclaredMethod(
                            "solarToLunar",
                            int.class,
                            int.class,
                            int.class);
                } catch (Throwable ignored) {
                    lunarCalendarSolarToLunarMethod = null;
                }
            }
            reflectionResolved = true;
        }
    }

    private static String formatWithFallback(int year, int month, int day) {
        try {
            return buildLunarText(solarToLunarFallback(year, month, day));
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String buildLunarText(int[] lunarDate) {
        if (lunarDate == null || lunarDate.length < 4) {
            return "";
        }
        int year = lunarDate[0];
        int month = lunarDate[1];
        int day = lunarDate[2];
        boolean leapMonth = lunarDate[3] == 1;
        if (year <= 0 || month <= 0 || month > CHINESE_MONTH_NUMBER.length || day <= 0 || day > 30) {
            return "";
        }
        return cyclicalm(year - 1864)
                + (leapMonth ? "闰" : "")
                + CHINESE_MONTH_NUMBER[month - 1]
                + "月"
                + getChinaDayString(day);
    }

    private static int[] solarToLunarFallback(int year, int month, int day) {
        int safeYear = clamp(year, 1899, 2099);
        int safeMonth = clamp(month, 1, 12);
        int safeDay = clamp(day, 1, 31);
        GregorianCalendar baseCalendar = new GregorianCalendar(1899, Calendar.FEBRUARY, 10);
        baseCalendar.setTimeZone(UTC);
        GregorianCalendar targetCalendar = new GregorianCalendar(safeYear, safeMonth - 1, safeDay);
        targetCalendar.setTimeZone(UTC);
        int offsetDays = (int) ((targetCalendar.getTimeInMillis() - baseCalendar.getTimeInMillis())
                / 86400000L);
        int lunarYear = 1899;
        int daysInYear = 0;
        while (lunarYear <= 2099 && offsetDays > 0) {
            daysInYear = daysInLunarYear(lunarYear);
            offsetDays -= daysInYear;
            lunarYear++;
        }
        if (offsetDays < 0) {
            offsetDays += daysInYear;
            lunarYear--;
        }
        int leapMonth = leapMonth(lunarYear);
        int lunarMonth = 1;
        int daysInMonth = 0;
        while (lunarMonth <= 13 && offsetDays > 0) {
            daysInMonth = daysInLunarMonth(lunarYear, lunarMonth);
            offsetDays -= daysInMonth;
            lunarMonth++;
        }
        if (offsetDays < 0) {
            offsetDays += daysInMonth;
            lunarMonth--;
        }
        int isLeapMonth = 0;
        if (leapMonth != 0 && lunarMonth > leapMonth && lunarMonth - 1 == leapMonth) {
            isLeapMonth = 1;
        }
        int displayMonth = lunarMonth;
        if (leapMonth != 0 && lunarMonth > leapMonth) {
            displayMonth = lunarMonth - 1;
        }
        return new int[]{lunarYear, displayMonth, offsetDays + 1, isLeapMonth};
    }

    private static int daysInLunarYear(int year) {
        int totalDays = leapMonth(year) != 0 ? 377 : 348;
        int monthInfo = FALLBACK_LUNAR_INFO[year - 1899] & 1048448;
        for (int bit = 524288; bit > 7; bit >>= 1) {
            if ((monthInfo & bit) != 0) {
                totalDays++;
            }
        }
        return totalDays;
    }

    private static int daysInLunarMonth(int year, int month) {
        int safeYear = clamp(year, 1899, 2099);
        return (FALLBACK_LUNAR_INFO[safeYear - 1899] & (1048576 >> month)) == 0 ? 29 : 30;
    }

    private static int leapMonth(int year) {
        int safeYear = clamp(year, 1899, 2099);
        return (FALLBACK_LUNAR_INFO[safeYear - 1899] & 15728640) >> 20;
    }

    private static String getChinaDayString(int day) {
        String[] prefixes = {"初", "十", "廿", "三"};
        int remainder = day % 10;
        int index = remainder == 0 ? 9 : remainder - 1;
        if (day > 30) {
            return "";
        }
        if (day == 10) {
            return "初十";
        }
        if (day == 20) {
            return "二十";
        }
        return prefixes[day / 10] + CHINESE_NUMBER[index];
    }

    private static String cyclicalm(int value) {
        String[] heavenlyStems = {
                "甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸"
        };
        String[] earthlyBranches = {
                "子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"
        };
        return heavenlyStems[value % 10] + earthlyBranches[value % 12] + "年";
    }

    private static String safeString(Object value) {
        if (!(value instanceof CharSequence)) {
            return "";
        }
        String text = value.toString().trim();
        return text.isEmpty() ? "" : text;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
