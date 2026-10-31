package com.campusfix.file;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储抽象。一期只有 local 实现，二期接入 OSS / 小程序直传时新增实现即可，
 * 上层不感知存储介质（见《系统设计说明书》ADR-03）。
 */
public interface FileStorageService {

    /**
     * 上传文件。
     *
     * @param file    文件
     * @param bizPath 业务目录，如 order/2026/10/30
     * @return 存储键（objectKey），数据库只存该值
     */
    String upload(MultipartFile file, String bizPath);

    /** 由 objectKey 得到可访问 URL。 */
    String getUrl(String objectKey);

    /** 删除。 */
    void delete(String objectKey);
}
