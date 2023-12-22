package com.letter.report.dynamicDataSource.Config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.letter.report.dynamicDataSource.service.DynamicDataSourceService;

@Component
public class DataSourceLogger {
@Autowired
private  DynamicDataSourceService dynamicDataSourceService;

@EventListener(ContextRefreshedEvent.class)
public void logDataSourceInfo() {
	DataSource dynamicDataSource = dynamicDataSourceService.getCurrentDataSource();
	System.out.println(dynamicDataSource.getClass().getName());

}
}
