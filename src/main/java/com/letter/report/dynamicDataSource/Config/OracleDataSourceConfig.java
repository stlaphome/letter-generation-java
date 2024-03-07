package com.letter.report.dynamicDataSource.Config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
@Configuration
public class OracleDataSourceConfig {
	@Value("${spring.datasource.hfsbeta.driver-class-name}")
	private String oracleDriverName;
	
	@Value("${spring.datasource.hfsbeta.url}")
	private String oracleUrl;
	
	@Value("${spring.datasource.hfsbeta.username}")
	private String oracleUsername;
	
	@Value("${spring.datasource.hfsbeta.password}")
	private String oraclePassword;
	
	 @Bean(name = "oracleDataSource")
	public DataSource getDataSource() throws Exception {
		final DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(oracleDriverName);
		dataSource.setUrl(oracleUrl);
		dataSource.setUsername(oracleUsername);
		dataSource.setPassword(oraclePassword);
		return dataSource;
		
	}
}
