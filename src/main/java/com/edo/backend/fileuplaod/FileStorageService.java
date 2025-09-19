package com.edo.backend.fileuplaod;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
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

    private final FileMetadataRepository fileRepo;

    // 기존 saveFile에서 store로 변경
    public FileMetadata store(MultipartFile file) throws IOException {
        Path uploadPath = Path.of(uploadDir);
        Files.createDirectories(uploadPath);

        String fileId = UUID.randomUUID().toString();

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "unnamed");
        String ext = getExt(originalName);

        String savedName = ext.isBlank() ? fileId : (fileId + "." + ext);

        Path targetPath = uploadPath.resolve(savedName);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        FileMetadata meta = new FileMetadata(
                fileId,
                originalName,
                file.getContentType(),
                file.getSize(),
                targetPath.toString()
        );
        return fileRepo.save(meta);
    }


//    public String saveFile(MultipartFile file) throws IOException {
//        // 저장 폴더 생성
//        Path uploadPath = Path.of(uploadDir);
//        Files.createDirectories(uploadPath);
//
//        // 파일명
//        String originalName = Objects.requireNonNull(file.getOriginalFilename());
//        String safeName = UUID.randomUUID() + "_" + originalName;
//
//        // 파일 저장
//        Path targetPath = uploadPath.resolve(safeName);
//        try (InputStream in = file.getInputStream()) {
//            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
//        }
//
//        return targetPath.toString(); // 저장 경로 반환
//    }

    public FileMetadata getMeta(String fileId) {
        return fileRepo.findById(fileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "file not found"));
    }

    public Resource load(String fileId) throws MalformedURLException {
        FileMetadata meta = getMeta(fileId);
        return new UrlResource(Path.of(meta.getStoragePath()).toUri());
    }

    private String getExt(String name) {
        int i = name.lastIndexOf('.');
        return (i < 0) ? "" : name.substring(i + 1);
    }
}
