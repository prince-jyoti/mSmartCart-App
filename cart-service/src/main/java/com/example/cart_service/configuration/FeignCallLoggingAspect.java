package com.example.cart_service.configuration;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class FeignCallLoggingAspect {

    @Pointcut("execution(* com.example.cart_service.client.*Client.*(..))")
    public void feignClientMethods() {}

    @Around("feignClientMethods()")
    public Object logAroundFeignCall(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        log.info("Before Feign call: {} args={}", method, Arrays.toString(pjp.getArgs()));

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("After Feign call: {} -> {} ({} ms)", method, result, System.currentTimeMillis() - start);
            return result;
        } catch (Throwable ex) {
            log.error("Feign client error in {}: {}", method, ex.getMessage(), ex);
            throw ex;
        }
    }
}
