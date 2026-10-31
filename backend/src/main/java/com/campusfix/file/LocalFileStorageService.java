package com.campusfix.file;

import com.campusfix.common.config.StorageProperties;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 本地存储实现，开发/演示环境使用。
 * 目录：{campusfix.storage.local-path}/{bizPath}/{uuid}.{ext}
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "campusfix.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private static final DateTimeFormatter DATE_DIR = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageProperties properties;

    @Override
    public String upload(MultipartFile file, String bizPath) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "文件为空");
        }
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String objectKey = buildObjectKey(bizPath, ext);
        Path target = Paths.get(properties.getLocalPath(), objectKey).toAbsolutePath().normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.error("本地存储写入失败 key={}", objectKey, e);
            throw new BizException(ResultCode.STORAGE_ERROR);
        }
        return objectKey;
    }

    @Override
    public String getUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        String path = properties.getLocalUrlPrefix() + "/" + objectKey;
        String base = properties.getPublicBaseUrl();
        if (!StringUtils.hasText(base)) {
            // 同源部署：返回相对路径，由页面所在域解析
            return path;
        }
        // 前后端分域 / CDN：返回绝对地址，避免图片请求打到前端域名
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return trimmed + path;
    }

    @Override
    public void delete(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(properties.getLocalPath(), objectKey).toAbsolutePath().normalize());
        } catch (IOException e) {
            log.warn("删除本地文件失败 key={}", objectKey, e);
        }
    }

    private String buildObjectKey(String bizPath, String ext) {
        String dir = StringUtils.hasText(bizPath) ? bizPath : "common";
        String dateDir = LocalDate.now().format(DATE_DIR);
        String name = UUID.randomUUID().toString().replace("-", "");
        String suffix = StringUtils.hasText(ext) ? "." + ext.toLowerCase() : "";
        return dir + "/" + dateDir + "/" + name + suffix;
    }
}
