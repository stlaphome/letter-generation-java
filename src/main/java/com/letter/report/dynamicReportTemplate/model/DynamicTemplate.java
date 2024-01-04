/**
 * 
 */
package com.letter.report.dynamicReportTemplate.model;

import java.sql.Blob;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import lombok.Data;

/**
 * @author arunkra
 *
 */
@Data
@Entity
@Table(name = "ST_TB_LMS_TEMPLATE_HDR")
public class DynamicTemplate {

	@Id
	@Column(name = "template_hdr_key")
	private int templateHeaderKey;
	
	@Column(name = "product_code")
	private String productCode;
	
	@Column(name = "template_name")
	private String templateName;
	
	@Column(name = "template_key")
	private String templateKey;
		
	@Column(name = "content")
	private Blob content;
	
	@Column(name = "active")
	private Boolean active;
	
	@Column(name = "created_by", nullable = false, updatable = false)
	private String createdBy;

	@CreatedDate
	@Column(name = "created_date", nullable = false, updatable = false)
	private Date createdDate;

	@Column(name = "last_modified_by")
	private String lastModifiedBy;

	@LastModifiedDate
	@Column(name = "last_modified_date")
	private Date lastModifiedDate;
}