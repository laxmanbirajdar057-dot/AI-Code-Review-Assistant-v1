package com.laxman.codereviewassistant.dto;

import lombok.Data;

@Data
public class RegisterRepoRequest {
    private String repoUrl;
    private String webhookSecret;
}