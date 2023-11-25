package com.st.lap.dynamicReportTemplate.repo;

import org.springframework.stereotype.Repository;

import com.st.lap.dynamicReportTemplate.model.DynamicReportContainer;

import javax.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.query.Procedure;

@Repository
public interface  DynamicReportContainerRepo extends JpaRepository<DynamicReportContainer, Integer>{
	@Transactional
	DynamicReportContainer findByReportFileName(String reportFileName);
}
