package com.wordspirit.config;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 对象存储服务
 * <p>优先使用阿里云 OSS；未配置 endpoint/bucket 时自动回退为本地文件存储，便于本地开发。</p>
 * <p>文件按「业务目录 / 年 / 月 / 日 / {userId}_{uuid}.后缀」归纳命名，例如：
 * avatar/2026/09/01/12_a1b2c3d4.jpg</p>
 */
@Slf4j
@Service
public class OssService {

    @Value("${aliyun.oss.endpoint:}")
    private String endpoint;

    @Value("${aliyun.oss.bucket:}")
    private String bucket;

    @Value("${aliyun.oss.access-key-id:}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret:}")
    private String accessKeySecret;

    @Value("${aliyun.oss.base-url:}")
    private String baseUrl;

    @Value("${aliyun.oss.dir.avatar:avatar}")
    private String avatarDir;

    @Value("${upload.dir:${user.dir}/uploads}")
    private String uploadDir;

    private OSS ossClient;

    @PostConstruct
    public void init() {
        if (StrUtil.isAllNotBlank(endpoint, bucket, accessKeyId, accessKeySecret)) {
            this.ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
            log.info("OSS 客户端初始化完成，bucket={}", bucket);
        } else {
            log.warn("未配置阿里云 OSS，头像上传将回退为本地文件存储（路径：{}）", uploadDir);
        }
    }

    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    /**
     * 上传头像
     *
     * @param file  上传的文件
     * @param userId 用户ID（用于文件名归类）
     * @return 可访问的文件地址
     */
    public String uploadAvatar(MultipartFile file, Long userId) {
        String ext = parseExt(file.getOriginalFilename());
        if (ossClient != null) {
            String key = buildKey(avatarDir, userId, ext);
            try (InputStream in = file.getInputStream()) {
                ossClient.putObject(bucket, key, in);
                return buildAccessUrl(key);
            } catch (IOException e) {
                throw new RuntimeException("头像上传到 OSS 失败", e);
            }
        }
        return saveLocal(file, ext);
    }

    /** 按业务目录+日期+用户ID构造对象名，实现文件名归纳分类 */
    private String buildKey(String dir, Long userId, String ext) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = IdUtil.fastSimpleUUID();
        return dir + "/" + date + "/" + userId + "_" + uuid + "." + ext;
    }

    /** 拼接对外访问地址 */
    private String buildAccessUrl(String key) {
        if (StrUtil.isNotBlank(baseUrl)) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            return base + "/" + key;
        }
        String host = endpoint.contains("://") ? endpoint.substring(endpoint.indexOf("://") + 3) : endpoint;
        return "https://" + bucket + "." + host + "/" + key;
    }

    /** 本地兜底：保存到 uploadDir/avatar 并返回相对访问路径 */
    private String saveLocal(MultipartFile file, String ext) {
        try {
            File dir = new File(uploadDir + "/avatar");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String filename = (file.getOriginalFilename() == null ? "avatar" : file.getOriginalFilename().replaceAll("[^\\w.\\-]", "_"));
            File dest = new File(dir, filename);
            file.transferTo(dest);
            return "/uploads/avatar/" + filename;
        } catch (IOException e) {
            throw new RuntimeException("头像本地保存失败", e);
        }
    }

    private String parseExt(String original) {
        if (original != null && original.lastIndexOf('.') > 0) {
            return original.substring(original.lastIndexOf('.') + 1).toLowerCase();
        }
        return "jpg";
    }
}
