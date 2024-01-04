package com.letter.report.dynamicReportTemplate.repo;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.letter.report.dynamicReportTemplate.model.DynamicReportContainer;

@Repository
public interface  DynamicReportContainerRepo extends JpaRepository<DynamicReportContainer, Integer>{
	@Transactional
	DynamicReportContainer findByReportFileName(String reportFileName);
}
