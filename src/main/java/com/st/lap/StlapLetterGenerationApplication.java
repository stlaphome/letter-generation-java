package com.st.lap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.st.lap.dynamicReportTemplate.service.DynamicTemplateService;


@SpringBootApplication
@EnableScheduling
public class StlapLetterGenerationApplication implements ApplicationRunner{
	
	@Autowired
	DynamicTemplateService dynamicTemplateService;

	public static void main(String[] args) {
		SpringApplication.run(StlapLetterGenerationApplication.class, args);
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		insertProductData();
	}

	private void insertProductData() {
		dynamicTemplateService.insertProductData();
	}
}