package com.xk.utils;

/**
 * 类型转换工具类
 * 用于安全地处理MyBatis返回的Map中的类型转换问题
 * MyBatis映射数据库整数列到Map时可能返回Long类型
 */
public class TypeConversionUtils {
    
    /**
     * 安全地将Object转换为Integer
     * @param obj 要转换的对象
     * @return 转换后的Integer，如果转换失败返回null
     */
    public static Integer toInteger(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return ((Long) obj).intValue();
        if (obj instanceof Number) return ((Number) obj).intValue();
        return null;
    }
    
    /**
     * 安全地将Object转换为int
     * @param obj 要转换的对象
     * @return 转换后的int，如果转换失败返回0
     */
    public static int toInt(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Integer) return (Integer) obj;
        if (obj instanceof Long) return ((Long) obj).intValue();
        if (obj instanceof Number) return ((Number) obj).intValue();
        return 0;
    }
    
    /**
     * 安全地将Object转换为Long
     * @param obj 要转换的对象
     * @return 转换后的Long，如果转换失败返回null
     */
    public static Long toLong(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Number) return ((Number) obj).longValue();
        return null;
    }
    
    /**
     * 安全地将Object转换为long
     * @param obj 要转换的对象
     * @return 转换后的long，如果转换失败返回0L
     */
    public static long tolong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Long) return (Long) obj;
        if (obj instanceof Integer) return ((Integer) obj).longValue();
        if (obj instanceof Number) return ((Number) obj).longValue();
        return 0L;
    }
    
    /**
     * 安全地将Object转换为String
     * @param obj 要转换的对象
     * @return 转换后的String，如果转换失败返回null
     */
    public static String toString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        return obj.toString();
    }
    
    /**
     * 安全地将Object转换为Boolean
     * @param obj 要转换的对象
     * @return 转换后的Boolean，如果转换失败返回null
     */
    public static Boolean toBoolean(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Boolean) return (Boolean) obj;
        if (obj instanceof Integer) return ((Integer) obj) != 0;
        if (obj instanceof Long) return ((Long) obj) != 0L;
        if (obj instanceof Number) return ((Number) obj).doubleValue() != 0.0;
        if (obj instanceof String) {
            String str = ((String) obj).toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str);
        }
        return null;
    }
    
    /**
     * 安全地将Object转换为boolean
     * @param obj 要转换的对象
     * @return 转换后的boolean，如果转换失败返回false
     */
    public static boolean toboolean(Object obj) {
        Boolean result = toBoolean(obj);
        return result != null && result;
    }
}