package com.st.lap.dynamicDataSource.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DynamicDataSourceService {
	@Autowired
	private final DataSource msSqlDataSource;
	@Autowired
	private final DataSource oracleDataSource;

	private DataSource currentDataSource;
	public DynamicDataSourceService(
			@Qualifier("msSqlDataSource") DataSource msSqlDataSource,
			@Qualifier("oracleDataSource") DataSource oracleDataSource
			) {
		this.msSqlDataSource = msSqlDataSource;
		this.oracleDataSource = oracleDataSource;
		this.currentDataSource = msSqlDataSource; // Set the initial datasource
	}

	public void switchToOracleDataSource() {
		this.currentDataSource = oracleDataSource;
	}

	// Add similar methods for other datasources if needed

	public DataSource getCurrentDataSource() {
		return currentDataSource;
	}
}
