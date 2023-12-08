package com.st.lap;

import javax.sql.DataSource;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
@Configuration
@PropertySource(value = {"file:${STLAP_LMS}/lms_application.properties"})
public class DataSourceConfig {
	
	@Value("${spring.datasource.driver-class-name}")
	private String driverName;
	
	@Value("${spring.datasource.url}")
	private String url;
	
	@Value("${spring.datasource.username}")
	private String username;
	
	@Value("${spring.datasource.password}")
	private String password;
	
	
	 @Bean
	public DataSource getDataSource() throws Exception {
		
		final DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(driverName);
		dataSource.setUrl(url);
		dataSource.setUsername(username);
		dataSource.setPassword(password);
		return dataSource;
		
	}
	

	

	
}
