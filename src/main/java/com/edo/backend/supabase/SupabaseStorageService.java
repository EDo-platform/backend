package com.edo.backend.supabase;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;

@Service
@RequiredArgsConstructor
public class SupabaseStorageService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.anon-key}")
    private String anonKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public void uploadFile(String bucket, String path, byte[] data, String contentType) {
        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + anonKey);
        headers.set("apikey", anonKey);
        headers.setContentType(MediaType.parseMediaType(contentType));

        HttpEntity<byte[]> request = new HttpEntity<>(data, headers);

        ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, request, String.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Upload failed: " + response.getBody());
        }
    }

    public byte[] downloadFile(String bucket, String path) {
        String url = supabaseUrl + "/storage/v1/object/" + bucket + "/" + path;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + anonKey);
        headers.set("apikey", anonKey);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Download failed");
        }

        return response.getBody();
    }
}
