package com.edo.backend;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileStorageService {

    @Value("${app.upload-dir}")
    private String uploadDir;

    public String saveFile(MultipartFile file) throws IOException {
        // 저장 폴더 생성
        Path uploadPath = Path.of(uploadDir);
        Files.createDirectories(uploadPath);

        // 파일명
        String originalName = Objects.requireNonNull(file.getOriginalFilename());
        String safeName = UUID.randomUUID() + "_" + originalName;

        // 파일 저장
        Path targetPath = uploadPath.resolve(safeName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return targetPath.toString(); // 저장 경로 반환
    }
}
