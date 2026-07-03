package com.xk.aspect;

import com.xk.service.CacheClearService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

@Aspect
@Component
public class CacheClearAspect {

    @Autowired
    private CacheClearService cacheClearService;

    /**
     * 定义切点：匹配所有 Service 实现类中以以下关键字开头的方法
     */
    @Pointcut("execution(* com.xk.service.impl.*.*(..)) && " +
            "(execution(* *update*(..)) || execution(* *save*(..)) || " +
            "execution(* *delete*(..)) || execution(* *confirm*(..)) || " +
            "execution(* *fire*(..)) || execution(* *approve*(..)) || " +
            "execution(* *checkOut*(..)) || execution(* *merchantCheckOut*(..)) || " +
            "execution(* *mark*(..)) || execution(* *cancel*(..)) || " +
            "execution(* *accept*(..)) || execution(* *reject*(..)))")
    public void businessMethod() {}

    @AfterReturning("businessMethod()")
    public void clearCache(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        Integer studentId = null;
        Integer jobId = null;

        // 1. 根据方法名和参数推断学生ID和兼职ID
        // 遍历参数，查找 Integer 类型的 studentId 或 jobId
        for (Object arg : args) {
            if (arg == null) continue;
            // 如果参数是 Integer，进一步判断其角色
            if (arg instanceof Integer) {
                int val = (Integer) arg;
                if (val <= 0) continue;

                // 根据参数名推断（需要编译时保留参数名，默认 Spring Boot 是开启的）
                // 获取参数名数组（JDK 8+ 需要 -parameters，Spring Boot 默认开启）
                String[] paramNames = getParameterNames(joinPoint);
                if (paramNames != null) {
                    for (int i = 0; i < paramNames.length && i < args.length; i++) {
                        if (("studentId".equals(paramNames[i]) || "userId".equals(paramNames[i])) && args[i] instanceof Integer) {
                            studentId = (Integer) args[i];
                        }
                        if ("jobId".equals(paramNames[i]) && args[i] instanceof Integer) {
                            jobId = (Integer) args[i];
                        }
                    }
                }

                // 如果通过参数名没找到，则根据方法名中的关键词推断
                if (studentId == null && (methodName.contains("Student") || methodName.contains("User") ||
                        methodName.contains("TimePreference") || methodName.contains("WorkingStatus"))) {
                    studentId = val;
                }
                if (jobId == null && (methodName.contains("Job") || methodName.contains("Assignment") ||
                        methodName.contains("Requirement"))) {
                    jobId = val;
                }
            }
            // 如果参数是对象，尝试从对象中提取 ID（例如 Job 或 User 对象）
            else if (arg instanceof com.xk.entity.User) {
                studentId = ((com.xk.entity.User) arg).getId();
            }
            else if (arg instanceof com.xk.entity.Job) {
                jobId = ((com.xk.entity.Job) arg).getId();
            }
        }

        // 2. 清除缓存
        if (studentId != null && studentId > 0) {
            cacheClearService.clearStudentCache(studentId);
            System.out.println("[AOP] 清除学生缓存, studentId=" + studentId + ", 方法=" + methodName);
        }
        if (jobId != null && jobId > 0) {
            cacheClearService.clearJobCache(jobId);
            System.out.println("[AOP] 清除兼职缓存, jobId=" + jobId + ", 方法=" + methodName);
        }
    }

    /**
     * 获取方法参数名（需要编译时加上 -parameters 参数，Spring Boot 默认支持）
     */
    private String[] getParameterNames(JoinPoint joinPoint) {
        try {
            Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
            return java.util.stream.IntStream.range(0, method.getParameterCount())
                    .mapToObj(i -> method.getParameters()[i].getName())
                    .toArray(String[]::new);
        } catch (Exception e) {
            return null;
        }
    }
}