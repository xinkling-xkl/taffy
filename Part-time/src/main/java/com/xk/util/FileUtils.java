package com.xk.util;

import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 文件工具类
 */
public class FileUtils {

    /**
     * 将输入流复制到文件
     */
    public static void copyInputStreamToFile(InputStream inputStream, File targetFile) throws IOException {
        if (targetFile.getParentFile() != null) {
            targetFile.getParentFile().mkdirs();
        }
        Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * 上传图片文件
     * @param file 上传的文件
     * @param uploadPath 上传路径
     * @param folder 子文件夹（如：avatar, feedback）
     * @return 文件的相对路径
     */
    public static String uploadImage(MultipartFile file, String uploadPath, String folder) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传的文件不能为空");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片文件");
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String newFilename = UUID.randomUUID().toString() + extension;

        // 构建文件路径
        String folderPath = uploadPath + File.separator + folder;
        File folderFile = new File(folderPath);
        if (!folderFile.exists()) {
            folderFile.mkdirs();
        }

        // 保存文件
        File targetFile = new File(folderPath + File.separator + newFilename);
        file.transferTo(targetFile);

        // 返回相对路径
        return "/" + folder + "/" + newFilename;
    }

    /**
     * 删除文件
     * @param filePath 文件路径
     * @param uploadPath 上传根路径
     * @return 是否删除成功
     */
    public static boolean deleteFile(String filePath, String uploadPath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        File file = new File(uploadPath + filePath);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }
}
