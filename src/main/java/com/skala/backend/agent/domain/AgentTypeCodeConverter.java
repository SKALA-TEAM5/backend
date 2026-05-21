package com.skala.backend.agent.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AgentTypeCodeConverter implements AttributeConverter<AgentTypeCode, String> {

    @Override
    public String convertToDatabaseColumn(AgentTypeCode attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public AgentTypeCode convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AgentTypeCode.from(dbData);
    }
}
