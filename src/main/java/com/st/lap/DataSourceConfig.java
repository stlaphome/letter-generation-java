package com.st.lap;

import javax.sql.DataSource;

import org.hibernate.jpa.boot.spi.EntityManagerFactoryBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
//@PropertySource(value = {"file:${STLAP_LMS}/lms_application.properties"})
public class DataSourceConfig {
	
	@Value("${spring.datasource.driver-class-name}")
	private String driverName;
	
	@Value("${spring.datasource.url}")
	private String url;
	
	@Value("${spring.datasource.username}")
	private String username;
	
	@Value("${spring.datasource.password}")
	private String password;
	
	@Value("${spring.datasource.datasource-oracle.driver-class-name}")
	private String oracleDriverName;
	
	@Value("${spring.datasource.datasource-oracle.url}")
	private String oracleUrl;
	
	@Value("${spring.datasource.datasource-oracle.username}")
	private String oracleUsername;
	
	@Value("${spring.datasource.datasource-oracle.password}")
	private String oraclePassword;
	
	@Primary
	 @Bean(name = "msSqlDataSource")
	public DataSource getDataSource() throws Exception {
		
		final DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(driverName);
		dataSource.setUrl(url);
		dataSource.setUsername(username);
		dataSource.setPassword(password);
		return dataSource;
		
	}
	
	@Primary
	 @Bean(name = "oracleDataSource")
	public DataSource getDataSourceOracle() throws Exception {
		final DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setDriverClassName(oracleDriverName);
		dataSource.setUrl(oracleUrl);
		dataSource.setUsername(oracleUsername);
		dataSource.setPassword(oraclePassword);
		return dataSource;
		
	}
	

	
}
