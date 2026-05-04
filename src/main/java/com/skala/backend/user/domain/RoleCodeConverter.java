package com.skala.backend.user.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RoleCodeConverter implements AttributeConverter<RoleCode, String> {

	@Override
	public String convertToDatabaseColumn(RoleCode attribute) {
		return attribute == null ? null : attribute.getValue();
	}

	@Override
	public RoleCode convertToEntityAttribute(String dbData) {
		return dbData == null ? null : RoleCode.from(dbData);
	}
}
