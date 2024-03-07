package com.letter.report.dynamicReportTemplate.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Table(name = "ST_TB_LMS_DYNAMIC_TINY_EDITOR")
@AllArgsConstructor
@NoArgsConstructor
public class DynamicTinyEditor {
	@Id
	@Column(name = "mail_id")
	private String mailId;
	
	@Column(name = "api_key")
	private String apiKey;
}
