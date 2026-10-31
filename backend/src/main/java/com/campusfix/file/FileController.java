package com.campusfix.file;

import com.campusfix.common.config.OrderProperties;
import com.campusfix.common.exception.BizException;
import com.campusfix.common.result.Result;
import com.campusfix.common.result.ResultCode;
import com.campusfix.common.security.CurrentUserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传。一期统一走服务端中转（ADR-03），底层由 {@link FileStorageService} 决定落 local 还是 OSS。
 *
 * <p>校验三层：后缀白名单 → 魔数（防改扩展名） → 大小上限。
 * 数据库只存返回的 {@code objectKey}，访问 URL 由存储实现生成。</p>
 */
@Slf4j
@Tag(name = "文件服务")
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_STAGE = Set.of("REPORT", "BEFORE", "AFTER");

    private final FileStorageService storageService;
    private final OrderProperties orderProperties;

    @Operation(summary = "上传图片（报修 / 维修前 / 维修后）")
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "REPORT") String stage) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_INVALID, "请选择要上传的图片");
        }
        String stageUpper = stage.toUpperCase(Locale.ROOT);
        if (!ALLOWED_STAGE.contains(stageUpper)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "图片阶段只能是 REPORT / BEFORE / AFTER");
        }

        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "仅支持 jpg / jpeg / png / webp 格式");
        }
        long limit = (long) orderProperties.getMaxImageMb() * 1024 * 1024;
        if (file.getSize() > limit) {
            throw new BizException(ResultCode.IMAGE_LIMIT,
                    "单张图片不能超过 " + orderProperties.getMaxImageMb() + "MB");
        }
        if (!isRealImage(file)) {
            throw new BizException(ResultCode.FILE_TYPE_NOT_ALLOWED, "文件内容不是有效图片，请重新选择");
        }

        String objectKey = storageService.upload(file, "order/" + CurrentUserHolder.id());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("objectKey", objectKey);
        data.put("url", storageService.getUrl(objectKey));
        data.put("fileName", file.getOriginalFilename());
        data.put("fileSize", file.getSize());
        data.put("stage", stageUpper);
        return Result.ok(data);
    }

    /** 魔数校验：JPEG(FF D8 FF) / PNG(89 50 4E 47) / WEBP(RIFF....WEBP)。 */
    private boolean isRealImage(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] head = in.readNBytes(12);
            if (head.length < 12) {
                return false;
            }
            boolean jpeg = (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF;
            boolean png = (head[0] & 0xFF) == 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
            boolean webp = head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                    && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
            return jpeg || png || webp;
        } catch (IOException e) {
            log.warn("读取上传文件头失败", e);
            return false;
        }
    }
}
