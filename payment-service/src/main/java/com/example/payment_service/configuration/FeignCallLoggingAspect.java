package com.example.payment_service.configuration;

import com.example.payment_service.exception.NotFoundException;
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

    @Pointcut("execution(* com.example.payment_service.client.*Client.*(..))")
    public void feignClientMethods() {}

    @Around("feignClientMethods()")
    public Object logAroundFeignCall(ProceedingJoinPoint pjp) throws Throwable {
        String method = pjp.getSignature().toShortString();
        log.debug("Before Feign call: {} args={}", method, Arrays.toString(pjp.getArgs()));

        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.debug("After Feign call: {} -> {} ({} ms)", method, result, System.currentTimeMillis() - start);
            return result;
        } catch (NotFoundException | IllegalArgumentException | IllegalStateException ex) {
            // The other service answered 404/400/409: a normal outcome, not a failure.
            log.warn("Feign call {} rejected: {}", method, ex.getMessage());
            throw ex;
        } catch (Throwable ex) {
            log.error("Feign client error in {}: {}", method, ex.getMessage(), ex);
            throw ex;
        }
    }
}
