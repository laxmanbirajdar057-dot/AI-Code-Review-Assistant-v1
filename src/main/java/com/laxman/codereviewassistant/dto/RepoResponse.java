package com.laxman.codereviewassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RepoResponse {
    private Long id;
    private String repoUrl;
    private String webhookUrl;
}