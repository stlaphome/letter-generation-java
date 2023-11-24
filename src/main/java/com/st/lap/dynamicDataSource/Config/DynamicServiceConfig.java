package com.st.lap.dynamicDataSource.Config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.st.lap.dynamicDataSource.service.DynamicDataSourceService;

@Configuration
public class DynamicServiceConfig {
	
	@Autowired
	@Qualifier("msSqlDataSource")
	DataSource msSqlDataSource;
	
	@Autowired
	@Qualifier("oracleDataSource") 
	DataSource oracleDataSource;
	
	@Bean
	public DynamicDataSourceService dynamicDataSourceService() throws Exception {
		return new DynamicDataSourceService(msSqlDataSource, oracleDataSource);
	}

}
