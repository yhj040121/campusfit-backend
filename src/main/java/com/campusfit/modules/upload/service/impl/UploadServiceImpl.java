package com.campusfit.modules.upload.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.upload.config.AliyunOssProperties;
import com.campusfit.modules.upload.service.UploadService;
import com.campusfit.modules.upload.vo.UploadImageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of(
        "image/jpg",
        "image/jpeg",
        "image/png",
        "image/webp"
    );

    private final AliyunOssProperties properties;

    public UploadServiceImpl(AliyunOssProperties properties) {
        this.properties = properties;
    }

    @Override
    public UploadImageVO uploadImage(MultipartFile file) {
        long currentUserId = UserAuthContext.requireUserId();
        return uploadInternal(file, buildPostImageObjectKey(currentUserId, resolveExtension(file)));
    }

    @Override
    public UploadImageVO uploadAvatar(MultipartFile file) {
        Long currentUserId = UserAuthContext.getCurrentUserId();
        return uploadInternal(file, buildAvatarObjectKey(currentUserId, resolveExtension(file)));
    }

    @Override
    public UploadImageVO uploadProfileCover(MultipartFile file) {
        long currentUserId = UserAuthContext.requireUserId();
        return uploadInternal(file, buildProfileCoverObjectKey(currentUserId, resolveExtension(file)));
    }

    private UploadImageVO uploadInternal(MultipartFile file, String objectKey) {
        validateConfig();
        validateFile(file);

        String contentType = normalizeContentType(file.getContentType());
        PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(objectKey)
            .contentLength(file.getSize());
        if (contentType != null) {
            requestBuilder.contentType(contentType);
        }

        try (S3Client s3Client = buildClient(); InputStream inputStream = file.getInputStream()) {
            s3Client.putObject(requestBuilder.build(), RequestBody.fromInputStream(inputStream, file.getSize()));
        } catch (IOException exception) {
            throw new BusinessException("读取图片失败，请重新选择后再试。");
        } catch (RuntimeException exception) {
            log.error("Failed to upload image to Aliyun OSS, bucket={}, endpoint={}, region={}",
                properties.getBucket(), properties.getEndpoint(), properties.getRegion(), exception);
            throw new BusinessException("图片上传失败，请检查阿里云 OSS 配置后重试。");
        }

        return new UploadImageVO(
            buildPublicUrl(objectKey),
            objectKey,
            safeFileName(file.getOriginalFilename()),
            file.getSize(),
            contentType == null ? "image/" + resolveExtension(file) : contentType
        );
    }

    private void validateConfig() {
        if (isBlank(properties.getAccessKeyId()) || isBlank(properties.getAccessKeySecret())) {
            throw new BusinessException("图片上传配置未完成，请先设置阿里云 OSS 的 AccessKey。");
        }
        if (isBlank(properties.getBucket()) || isBlank(properties.getEndpoint()) || isBlank(properties.getRegion())) {
            throw new BusinessException("图片上传配置不完整，请检查 Bucket、Endpoint 和 Region。");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请先选择要上传的图片。");
        }
        if (file.getSize() > properties.getMaxFileSizeBytes()) {
            throw new BusinessException("图片大小不能超过 10MB。");
        }

        String extension = resolveExtension(file);
        Set<String> allowedExtensions = new LinkedHashSet<>();
        for (String value : properties.getAllowedExtensions()) {
            if (value != null && !value.isBlank()) {
                allowedExtensions.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessException("仅支持 jpg、jpeg、png、webp 格式的图片。");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (contentType != null && !IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException("仅支持 jpg、jpeg、png、webp 格式的图片。");
        }
    }

    private S3Client buildClient() {
        return S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKeyId().trim(), properties.getAccessKeySecret().trim())
            ))
            .endpointOverride(URI.create(normalizeAwsSdkEndpoint(properties.getEndpoint())))
            .region(resolveAwsRegion())
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(false)
                .chunkedEncodingEnabled(false)
                .build())
            .build();
    }

    private String buildPostImageObjectKey(long currentUserId, String extension) {
        LocalDate today = LocalDate.now();
        return String.format(
            Locale.ROOT,
            "post-images/%d/%04d/%02d/%s.%s",
            currentUserId,
            today.getYear(),
            today.getMonthValue(),
            UUID.randomUUID().toString().replace("-", ""),
            extension
        );
    }

    private String buildAvatarObjectKey(Long currentUserId, String extension) {
        LocalDate today = LocalDate.now();
        String ownerSegment = currentUserId == null ? "guest" : String.valueOf(currentUserId);
        return String.format(
            Locale.ROOT,
            "avatars/%s/%04d/%02d/%s.%s",
            ownerSegment,
            today.getYear(),
            today.getMonthValue(),
            UUID.randomUUID().toString().replace("-", ""),
            extension
        );
    }

    private String buildProfileCoverObjectKey(long currentUserId, String extension) {
        LocalDate today = LocalDate.now();
        return String.format(
            Locale.ROOT,
            "profile-covers/%d/%04d/%02d/%s.%s",
            currentUserId,
            today.getYear(),
            today.getMonthValue(),
            UUID.randomUUID().toString().replace("-", ""),
            extension
        );
    }

    private String buildPublicUrl(String objectKey) {
        String publicBaseUrl = properties.getPublicBaseUrl();
        if (isBlank(publicBaseUrl)) {
            String endpoint = normalizePublicEndpoint(properties.getEndpoint());
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            if (host != null && host.startsWith("s3.oss-") && host.endsWith(".aliyuncs.com")) {
                host = host.substring(3);
            }
            publicBaseUrl = uri.getScheme() + "://" + properties.getBucket() + "." + host;
        }
        return stripTrailingSlash(publicBaseUrl) + "/" + objectKey;
    }

    private String resolveExtension(MultipartFile file) {
        String originalFilename = safeFileName(file.getOriginalFilename());
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex > -1 && dotIndex < originalFilename.length() - 1) {
            return originalFilename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        String contentType = normalizeContentType(file.getContentType());
        if ("image/jpeg".equals(contentType)) {
            return "jpg";
        }
        if ("image/png".equals(contentType)) {
            return "png";
        }
        if ("image/webp".equals(contentType)) {
            return "webp";
        }
        throw new BusinessException("无法识别图片格式，请重新选择 jpg、jpeg、png、webp 图片。");
    }

    private String normalizeContentType(String contentType) {
        if (isBlank(contentType)) {
            return null;
        }
        String normalized = contentType.trim().toLowerCase(Locale.ROOT);
        int separatorIndex = normalized.indexOf(';');
        return separatorIndex > -1 ? normalized.substring(0, separatorIndex) : normalized;
    }

    private Region resolveAwsRegion() {
        String endpoint = normalizePublicEndpoint(properties.getEndpoint()).toLowerCase(Locale.ROOT);
        if (endpoint.contains(".aliyuncs.com")) {
            return Region.AWS_GLOBAL;
        }
        String region = properties.getRegion();
        if (isBlank(region)) {
            return Region.US_EAST_1;
        }
        return Region.of(region.trim());
    }

    private String normalizeAwsSdkEndpoint(String endpoint) {
        String normalized = normalizePublicEndpoint(endpoint);
        URI uri = URI.create(normalized);
        String host = uri.getHost();
        if (host != null && host.startsWith("oss-") && host.endsWith(".aliyuncs.com")) {
            host = "s3." + host;
            return uri.getScheme() + "://" + host;
        }
        return normalized;
    }

    private String normalizePublicEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        if (value.isEmpty()) {
            return "https://oss-cn-hangzhou.aliyuncs.com";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://" + value;
    }

    private String safeFileName(String originalFilename) {
        return originalFilename == null ? "" : originalFilename.trim();
    }

    private String stripTrailingSlash(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
