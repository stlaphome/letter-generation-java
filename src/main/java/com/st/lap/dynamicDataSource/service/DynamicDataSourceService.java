package com.st.lap.dynamicDataSource.service;

import javax.sql.DataSource;

public class DynamicDataSourceService {
	private final DataSource msSqlDataSource;
	private final DataSource oracleDataSource;

	private DataSource currentDataSource;

	public DynamicDataSourceService(DataSource msSqlDataSource,DataSource oracleDataSource) {
		this.msSqlDataSource = msSqlDataSource;
		this.oracleDataSource = oracleDataSource;
		this.currentDataSource = msSqlDataSource; // Set the initial datasource
	}

	public void switchToOracleDataSource() {
		this.currentDataSource = this.oracleDataSource;
	}

	// Add similar methods for other datasources if needed

	public DataSource getCurrentDataSource() {
		return currentDataSource;
	}
}
