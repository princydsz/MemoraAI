package com.memoraai.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class MinioBucketInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MinioBucketInitializer.class);

    private final MinioClient minioClient;
    private final String bucketName;
    private final boolean bucketInitEnabled;

    public MinioBucketInitializer(
            MinioClient minioClient,
            @Value("${minio.bucket-name:memora-media}") String bucketName,
            @Value("${minio.bucket-init-enabled:true}") boolean bucketInitEnabled) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.bucketInitEnabled = bucketInitEnabled;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!bucketInitEnabled) {
            log.info("MinIO bucket initialization is disabled by configuration (bucketInitEnabled=false).");
            return;
        }

        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );
                log.info("Initialized private MinIO bucket '{}' successfully.", bucketName);
            } else {
                log.info("MinIO bucket '{}' already exists and is ready for use.", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to connect to MinIO or initialize bucket '{}': {}", bucketName, e.getMessage());
            throw new IllegalStateException("Failed to connect to MinIO or initialize required bucket '" + bucketName + "'. Ensure MinIO is running and reachable.", e);
        }
    }
}
