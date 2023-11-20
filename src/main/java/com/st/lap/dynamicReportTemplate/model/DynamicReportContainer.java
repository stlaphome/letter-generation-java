package com.st.lap.dynamicReportTemplate.model;

import java.sql.Blob;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Data
@Entity
@Table(name = "ST_TB_LMS_DYNAMIC_REPORT_CONTAINER")
public class DynamicReportContainer {
	
	@Id
	@Column(name = "dynamic_report_container_hdr_key")
	private int dynamicReportContainerHeaderKey;
	
	@Column(name = "report_file_name")
	private String reportFileName;
	
	@Column(name = "report_file")
	private Blob reportFile;

}
