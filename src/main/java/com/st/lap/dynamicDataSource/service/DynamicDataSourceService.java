package com.st.lap.dynamicDataSource.service;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class DynamicDataSourceService {
	private final DataSource msSqlDataSource;
	private final DataSource oracleDataSource;

	private DataSource currentDataSource;

	public DynamicDataSourceService(
			@Qualifier("dataSourceMsSql") DataSource msSqlDataSource,
			@Qualifier("dataSourceOracle") DataSource oracleDataSource) {
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
