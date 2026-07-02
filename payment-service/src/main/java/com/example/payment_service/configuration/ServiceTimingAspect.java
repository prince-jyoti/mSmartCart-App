package com.example.payment_service.configuration;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class ServiceTimingAspect {

    @Around("execution(* com.example.payment_service.service..*(..))")
    public Object timeServiceMethod(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            return pjp.proceed();
        } finally {
            log.info("{} executed in {} ms", pjp.getSignature().toShortString(),
                    (System.nanoTime() - start) / 1_000_000);
        }
    }
}
