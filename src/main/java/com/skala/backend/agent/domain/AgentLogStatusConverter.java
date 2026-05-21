package com.skala.backend.agent.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class AgentLogStatusConverter implements AttributeConverter<AgentLogStatus, String> {

    @Override
    public String convertToDatabaseColumn(AgentLogStatus attribute) {
        return attribute == null ? null : attribute.getCode();
    }

    @Override
    public AgentLogStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : AgentLogStatus.from(dbData);
    }
}
