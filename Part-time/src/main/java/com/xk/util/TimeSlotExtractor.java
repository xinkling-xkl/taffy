package com.xk.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文自然语言时间槽提取器
 * "周六上午" → [{dayOfWeek:6, timeSlot:"morning"}]
 */
public class TimeSlotExtractor {

    private static final Map<String, Integer> DAY_MAP = new LinkedHashMap<>();
    static {
        DAY_MAP.put("周一", 1); DAY_MAP.put("星期一", 1);
        DAY_MAP.put("周二", 2); DAY_MAP.put("星期二", 2);
        DAY_MAP.put("周三", 3); DAY_MAP.put("星期三", 3);
        DAY_MAP.put("周四", 4); DAY_MAP.put("星期四", 4);
        DAY_MAP.put("周五", 5); DAY_MAP.put("星期五", 5);
        DAY_MAP.put("周六", 6); DAY_MAP.put("星期六", 6);
        DAY_MAP.put("周日", 7); DAY_MAP.put("周日", 7); DAY_MAP.put("星期天", 7); DAY_MAP.put("周天", 7);
    }

    private static final Map<String, List<String>> PERIOD_MAP = new LinkedHashMap<>();
    static {
        PERIOD_MAP.put("上午", Collections.singletonList("morning"));
        PERIOD_MAP.put("中午", Collections.singletonList("morning"));
        PERIOD_MAP.put("下午", Collections.singletonList("afternoon"));
        PERIOD_MAP.put("晚上", Collections.singletonList("evening"));
        PERIOD_MAP.put("夜晚", Collections.singletonList("evening"));
        PERIOD_MAP.put("白天", Arrays.asList("morning", "afternoon"));
        PERIOD_MAP.put("全天", Arrays.asList("morning", "afternoon", "evening"));
    }

    private static final Map<String, List<Integer>> RANGE_MAP = new LinkedHashMap<>();
    static {
        RANGE_MAP.put("工作日", Arrays.asList(1, 2, 3, 4, 5));
        RANGE_MAP.put("周末", Arrays.asList(6, 7));
    }

    /**
     * 输入 "帮我找周六上午有空的学生" → [{dayOfWeek:6, timeSlot:"morning"}]
     * 输入 "找周一到周五下午的兼职" → [{dayOfWeek:1,slot:afternoon}...{dayOfWeek:5,slot:afternoon}]
     * 输入 "周末高薪兼职" → [{dayOfWeek:6}, {dayOfWeek:7}]  没有时段信息
     * 返回空列表表示没有提取到时间信息
     */
    public static List<TimeSlot> extract(String userInput) {
        if (userInput == null || userInput.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<TimeSlot> result = new ArrayList<>();

        // 1. 提取时间范围词（工作日/周末）
        for (Map.Entry<String, List<Integer>> entry : RANGE_MAP.entrySet()) {
            if (userInput.contains(entry.getKey())) {
                List<String> periods = extractPeriod(userInput);
                for (Integer day : entry.getValue()) {
                    if (periods.isEmpty()) {
                        result.add(new TimeSlot(day, null));
                    } else {
                        for (String p : periods) {
                            result.add(new TimeSlot(day, p));
                        }
                    }
                }
                if (!periods.isEmpty()) return deduplicate(result);
            }
        }

        // 2. 尝试 "X至Y" 范围模式："周一至周五" → 1,2,3,4,5
        Matcher rangeMatcher = Pattern.compile("(周[一二三四五六日天]|星期[一二三四五六日天])[至到\\-~](周[一二三四五六日天]|星期[一二三四五六日天])").matcher(userInput);
        if (rangeMatcher.find()) {
            Integer from = DAY_MAP.get(rangeMatcher.group(1));
            Integer to = DAY_MAP.get(rangeMatcher.group(2));
            if (from != null && to != null && from <= to) {
                List<String> periods = extractPeriod(userInput);
                for (int d = from; d <= to; d++) {
                    if (periods.isEmpty()) {
                        result.add(new TimeSlot(d, null));
                    } else {
                        for (String p : periods) {
                            result.add(new TimeSlot(d, p));
                        }
                    }
                }
                if (!result.isEmpty()) return deduplicate(result);
            }
        }

        // 3. 提取单个 "周X上午/下午/晚上" 组合
        // 先找所有 "X周X" 或 "星期X" 出现位置，再检查后面的时间段词
        List<Integer> singleDays = new ArrayList<>();
        Matcher dayMatcher = Pattern.compile("(周[一二三四五六日天]|星期[一二三四五六日天])").matcher(userInput);
        while (dayMatcher.find()) {
            Integer day = DAY_MAP.get(dayMatcher.group());
            if (day != null) singleDays.add(day);
        }
        List<String> periods = extractPeriod(userInput);

        if (!singleDays.isEmpty()) {
            for (Integer day : singleDays) {
                if (periods.isEmpty()) {
                    result.add(new TimeSlot(day, null));
                } else {
                    for (String p : periods) {
                        result.add(new TimeSlot(day, p));
                    }
                }
            }
            return deduplicate(result);
        }

        // 4. 只提取了时段没有日期（如"上午有空的学生"）
        if (!periods.isEmpty()) {
            for (String p : periods) {
                result.add(new TimeSlot(null, p));
            }
            return deduplicate(result);
        }

        return Collections.emptyList();
    }

    private static List<String> extractPeriod(String text) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : PERIOD_MAP.entrySet()) {
            if (text.contains(entry.getKey())) {
                result.addAll(entry.getValue());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(result));
    }

    private static List<TimeSlot> deduplicate(List<TimeSlot> list) {
        Set<String> seen = new LinkedHashSet<>();
        List<TimeSlot> result = new ArrayList<>();
        for (TimeSlot ts : list) {
            String key = ts.dayOfWeek + "_" + ts.timeSlot;
            if (seen.add(key)) result.add(ts);
        }
        return result;
    }

    public static class TimeSlot {
        public final Integer dayOfWeek;
        public final String timeSlot;

        public TimeSlot(Integer dayOfWeek, String timeSlot) {
            this.dayOfWeek = dayOfWeek;
            this.timeSlot = timeSlot;
        }

        @Override
        public String toString() {
            return "{" + dayOfWeek + ":" + timeSlot + "}";
        }
    }
}
