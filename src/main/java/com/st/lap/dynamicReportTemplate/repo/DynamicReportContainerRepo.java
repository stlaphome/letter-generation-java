package com.st.lap.dynamicReportTemplate.repo;

import org.springframework.stereotype.Repository;

import com.st.lap.dynamicReportTemplate.model.DynamicReportContainer;

import org.springframework.data.jpa.repository.JpaRepository;

@Repository
public interface  DynamicReportContainerRepo extends JpaRepository<DynamicReportContainer, Integer>{
	
	DynamicReportContainer findByReportFileName(String reportFileName);
}
