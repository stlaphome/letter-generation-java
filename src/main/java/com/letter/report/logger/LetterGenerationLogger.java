package com.letter.report.logger;

import java.util.HashMap;
import java.util.Map;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

@Aspect
@Component
public class LetterGenerationLogger {
	private static final Logger logger = LoggerFactory.getLogger(LetterGenerationLogger.class);

	@Autowired
	private ObjectMapper mapper;

	@Pointcut("within(com.letter.report..*)")
	public void pointcut() {
	}

	@Before("pointcut()")
	public void logMethod(JoinPoint joinPoint) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		logger.info("Class Name : " + signature.getDeclaringTypeName() + "    Method Name : " + signature.getName()
				+ "Method Started");

		Object[] args = joinPoint.getArgs();
		if (args != null && args.length > 0) {
			logger.info("Method Parameters:");
			for (int i = 0; i < args.length; i++) {
				logger.info("Parameter " + (i + 1) + ": " + args[i]);
			}
		}

	}

	@AfterReturning(pointcut = "pointcut()", returning = "entity")
	public void logMethodAfter(JoinPoint joinPoint, Object entity) {
		MethodSignature signature = (MethodSignature) joinPoint.getSignature();
		logger.info("Class Name : " + signature.getDeclaringTypeName() + "    Method Name : " + signature.getName()
				+ "Method Completed");
	}
}