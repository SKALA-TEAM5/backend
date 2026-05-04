package com.skala.backend.project.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProjectStatusCodeConverter implements AttributeConverter<ProjectStatusCode, String> {

	@Override
	public String convertToDatabaseColumn(ProjectStatusCode attribute) {
		return attribute == null ? null : attribute.getValue();
	}

	@Override
	public ProjectStatusCode convertToEntityAttribute(String dbData) {
		return dbData == null ? null : ProjectStatusCode.from(dbData);
	}
}
