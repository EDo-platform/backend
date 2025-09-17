package com.edo.backend.fileuplaod;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "files")
@Getter @Setter
@NoArgsConstructor // 매개변수가 없는 기본 생성자 자동생성
public class FileMetadata {
    @Id
    private String id; // fieldId

    private String originalName;
    private String contentType;
    private long size;

    private String storagePath; // 파일 경로(위치는 로컬 OR cloud 내)
    private Instant createdAt = Instant.now();

    public FileMetadata(String id, String originalName, String contentType, long size, String storagePath) {
        this.id = id;
        this.originalName = originalName;
        this.contentType = contentType;
        this.size = size;
        this.storagePath = storagePath;
    }


}
