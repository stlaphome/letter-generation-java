package com.letter.report.dynamicReportTemplate.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.letter.report.dynamicReportTemplate.model.DynamicTinyEditor;
@Repository
public interface DynamicTinyEditorRepo extends JpaRepository<DynamicTinyEditor, String>{

}
