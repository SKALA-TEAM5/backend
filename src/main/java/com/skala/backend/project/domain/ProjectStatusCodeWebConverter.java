package com.skala.backend.project.domain;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ProjectStatusCodeWebConverter implements Converter<String, ProjectStatusCode> {

	@Override
	public ProjectStatusCode convert(String source) {
		return ProjectStatusCode.from(source);
	}
}
