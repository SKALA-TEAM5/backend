package com.skala.backend.aiagent.domain;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class AiAgentTypeCodeWebConverter implements Converter<String, AiAgentTypeCode> {

	@Override
	public AiAgentTypeCode convert(String source) {
		return AiAgentTypeCode.from(source);
	}
}
