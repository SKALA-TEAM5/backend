INSERT INTO service.usage_categories (code, name)
VALUES
    ('CAT_01', '안전, 보건관리자 임금 등'),
    ('CAT_02', '안전시설비 등'),
    ('CAT_03', '보호구 등'),
    ('CAT_04', '안전보건진단비 등'),
    ('CAT_05', '안전보건교육비 등'),
    ('CAT_06', '근로자 건강장해예방비 등'),
    ('CAT_07', '건설재해예방전문지도기관 기술지도비'),
    ('CAT_08', '본사 전담 조직 근로자 임금 등'),
    ('CAT_09', '위험성평가 등에 따른 소요비용')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name;

INSERT INTO service.evidence_types (code, name, description)
VALUES
    ('tax_invoice', '전자세금계산서', '법인사업자 의무 발행 세금계산서(홈택스 발급사실 조회 포함)'),
    ('tax_invoice_confirm', '세금계산서 발급사실 조회', '홈택스 제3자 전자세금계산서 발급사실 조회 캡처본'),
    ('receipt', '영수증', '구매 또는 결제 사실을 확인할 수 있는 영수증'),
    ('transaction_statement', '거래명세표', '품목·수량·단가가 기재된 구매 거래명세서'),
    ('wage_statement', '임금명세서', '근로자 임금 지급 내역을 확인할 수 있는 임금명세서'),
    ('site_photo', '설치·시공 현황 사진', '안전시설물 설치 완료 상태를 촬영한 현장 사진'),
    ('item_photo', '물품 구매 현황 사진', '구매 물품 수량 확인을 위한 전체 촬영 사진'),
    ('wearing_photo', '보호구 착용 상태 사진', '안전모·안전대 등 보호구 착용 상태 확인 사진'),
    ('work_photo', '근무 현황 사진', '안전관리자·보조원 등의 실제 업무 수행 사진'),
    ('appointment_report', '선임신고서', '안전·보건관리자 선임 신고 확인서'),
    ('pay_stub', '급여명세서', '안전·보건관리자 등의 월별 급여 지급 내역서'),
    ('work_log', '업무일지', '안전관리자·보조원의 일별 업무 수행 기록지'),
    ('daily_output_log', '출력일보', '일별 현장 근로자 출력 인원 현황 기록지'),
    ('inspection_log', '점검일지', '안전점검 실시 내용 및 결과 기록지'),
    ('supply_ledger', '보호구 지급대장', '보호구 지급 대상자·수량·일자 관리 대장'),
    ('inventory_ledger', '입출고 관리대장', '보호구 입고·출고 내역 재고 관리 대장'),
    ('edu_confirm', '교육 확인서', '안전보건 교육 실시 사실 확인서'),
    ('edu_attendance', '교육 대상 명단', '교육 수강 대상자 명단 및 서명부'),
    ('transfer_confirm', '이체 확인증', '교육비·진단비 등 계좌이체 완료 캡처 증빙'),
    ('health_checkup_result', '건강검진 결과서', '근로자 건강검진 실시 결과 확인서'),
    ('health_checkup_contract', '건강검진 계약서', '건강검진 기관과 체결한 용역 계약서'),
    ('tech_guidance_contract', '기술지도 계약서', '기술지도기관과 발주자가 체결한 기술지도 계약서'),
    ('tech_guidance_report', '기술지도 결과 보고서', '기술지도 실시 후 작성된 점검 결과 보고서'),
    ('tech_guidance_photo', '기술지도 점검 사진', '기술지도 현장 점검 시 촬영한 사진'),
    ('usage_statement', '안전관리비 사용내역서', '월별 안전관리비 사용명세서(별지 1호 서식)'),
    ('analysis_table', '안전관리비 분석표', '착수~현재 누적 사용액 항목별 예산 현황표'),
    ('purchase_detail', '구매 내역서', '세금계산서 발행 전 품목·수량·금액 확인 내역서')
ON CONFLICT (code) DO UPDATE
SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;
