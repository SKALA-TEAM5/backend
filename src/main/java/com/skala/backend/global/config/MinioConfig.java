package com.skala.backend.global.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

	@Bean
	public MinioClient minioClient(
			@Value("${app.file-storage.endpoint}") String endpoint,
			@Value("${app.file-storage.access-key}") String accessKey,
			@Value("${app.file-storage.secret-key}") String secretKey
	) {
		return MinioClient.builder()
				.endpoint(endpoint)
				.credentials(accessKey, secretKey)
				.build();
	}

	@Bean
	public CommandLineRunner ensureBucket(
			MinioClient minioClient,
			@Value("${app.file-storage.bucket}") String bucket
	) {
		return args -> {
			try {
				boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
				if (!exists) {
					minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
				}
			} catch (Exception e) {
				// MinIO 미연결 시 컨텍스트 시작은 허용. 파일 API 호출 시점에 실패.
				System.err.println("[MinIO] 버킷 초기화 실패 — MinIO 연결을 확인하세요: " + e.getMessage());
			}
		};
	}
}
