package com.st.lap.dynamicDataSource.Config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Configuration
public class OracleDataSourceConfig {
	@Value("${spring.datasource.datasource-oracle.driver-class-name}")
	private String oracleDriverName;
	
	@Value("${spring.datasource.datasource-oracle.url}")
	private String oracleUrl;
	
	@Value("${spring.datasource.datasource-oracle.username}")
	private String oracleUsername;
	
	@Value("${spring.datasource.datasource-oracle.password}")
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
