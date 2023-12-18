package com.st.lap.dynamicReportTemplate.controller;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.st.lap.dynamicReportTemplate.letterModel.DynamicTemplateModel;
import com.st.lap.dynamicReportTemplate.letterModel.GenerateTemplateModel;
import com.st.lap.dynamicReportTemplate.service.DynamicTemplateService;

@RestController
@RequestMapping("/dynamicTemplate")
public class DynamicTemplateController {
	
	@Autowired
	private DynamicTemplateService dynamicTemplateService;
	
	@PostMapping("/saveTemplate")
	public ResponseEntity<String> saveTemplate(
			@RequestBody DynamicTemplateModel dynamicTemplateModel) {
		return dynamicTemplateService.saveTemplate(dynamicTemplateModel);
	}
	
	@PostMapping("/getTemplate")
	public ResponseEntity<DynamicTemplateModel> getTemplate(
			@RequestBody DynamicTemplateModel dynamicTemplateModel) {
		return dynamicTemplateService.getTemplate(dynamicTemplateModel);
	}
	
	@PostMapping("/getTemplateKey")
	public ResponseEntity<List<Map>> getTemplateKey(
			@RequestBody Map<String,String> dataMap) {
		return dynamicTemplateService.getTemplateKey(dataMap);
	}
	
	@PostMapping("/getVariablesList")
	public ResponseEntity<List<String>> getVariablesList(@RequestBody DynamicTemplateModel dynamicTemplateModel) {
		return dynamicTemplateService.getVariablesList(dynamicTemplateModel.getTemplateName());
	}
	
	@GetMapping("/getTemplateNameList")
	public ResponseEntity<List<String>> getTemplateNameList(@RequestParam String productCode) {
		return dynamicTemplateService.getTemplateNameList(productCode);
	}
	
	@GetMapping("/getAllApplicationNumbers")
	public ResponseEntity<List<String>> getAllApplicationNumbers(@RequestParam String productCode) {
		return dynamicTemplateService.getAllApplicationNumbers(productCode);
	}
	
	@PostMapping("/getTemplateForView")
	public ResponseEntity<byte[]> getTemplateForView(
			@RequestBody Map<String,String> dataMap) {
		return dynamicTemplateService.getTemplateForView(dataMap);
	}
	
	@PostMapping("/generateLetter")
	public ResponseEntity<Map<String, Object>> generateLetter(
			@RequestBody GenerateTemplateModel model) throws SQLException {
		return dynamicTemplateService.generateLetter(model);
	}
	
	@PostMapping("/getGeneratedFile")
	public ResponseEntity<byte[]> getGeneratedFile(
			@RequestBody Map<String,String> dataMap) {
		return dynamicTemplateService.getGeneratedFile(dataMap.get("filePath"));
	}
	
	@PostMapping("/sendNotification")
	public ResponseEntity<String> sendNotification(
			@RequestBody Map<String,Object> dataMap) {
		return dynamicTemplateService.sendNotification(dataMap);
	}
	
	@PostMapping("/getProductTypeList")
	public ResponseEntity<List<Map<String,Object>>> getProductTypeList(){
		return dynamicTemplateService.getProductTypeList();
		
	}
	@PostMapping("/fetchDataBasedOnDB")
	public ResponseEntity<Map<String, Object>> fetchDataBasedOnDB(@RequestBody GenerateTemplateModel model){
		return dynamicTemplateService.fetchDataBasedOnDB(model);
		
	}
	
}
