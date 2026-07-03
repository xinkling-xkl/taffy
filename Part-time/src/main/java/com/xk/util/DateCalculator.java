package com.xk.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.Map;

public class DateCalculator {

    // 时段对应的结束时间（小时）
    private static final Map<String, Integer> SLOT_END_HOUR = Map.of(
            "morning", 13,
            "afternoon", 18,
            "evening", 22
    );

    /**
     * 根据星期几和时段，计算下一次可工作的具体日期（ >= today ）
     * @param dayOfWeek 目标星期几 (1=周一 ... 7=周日)
     * @param timeSlot 时段 (morning/afternoon/evening)
     * @param today 基准日期（通常为 LocalDate.now()）
     * @return 下一次可工作的日期（如果今天就是目标星期几且时段未过则返回今天，否则返回下周的对应星期几）
     */
    public static LocalDate getNextWorkDate(int dayOfWeek, String timeSlot, LocalDate today) {
        DayOfWeek target = DayOfWeek.of(dayOfWeek); // 1=Monday ... 7=Sunday
        LocalDate targetDate = today.with(TemporalAdjusters.nextOrSame(target));

        // 如果计算出的日期是今天，需要检查今天此时段是否已过
        if (targetDate.equals(today)) {
            LocalDateTime now = LocalDateTime.now();
            int currentHour = now.getHour();
            int endHour = SLOT_END_HOUR.getOrDefault(timeSlot, 13);
            if (currentHour >= endHour) {
                // 今天此时段已过，改为下周
                targetDate = today.with(TemporalAdjusters.next(target));
            }
        }
        return targetDate;
    }

    /**
     * 获取时段的中文名称
     */
    public static String getSlotChinese(String timeSlot) {
        switch (timeSlot) {
            case "morning": return "上午";
            case "afternoon": return "下午";
            case "evening": return "晚上";
            default: return timeSlot;
        }
    }
}