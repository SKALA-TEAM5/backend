package com.skala.backend.project.repository;

/**
 * 담당자 검색 후보 조회용 프로젝션. (userId, realName, roleCode)
 */
public interface AssigneeCandidateRow {

	Long getUserId();

	String getRealName();

	String getRoleCode();
}
