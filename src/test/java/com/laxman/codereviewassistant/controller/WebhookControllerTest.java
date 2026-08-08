package com.laxman.codereviewassistant.controller;

import com.laxman.codereviewassistant.entity.Repository;
import com.laxman.codereviewassistant.entity.WebhookEvent;
import com.laxman.codereviewassistant.repository.RepositoryRepository;
import com.laxman.codereviewassistant.repository.WebhookEventRepository;
import com.laxman.codereviewassistant.security.EncryptionService;
import com.laxman.codereviewassistant.security.WebhookSignatureValidator;
import com.laxman.codereviewassistant.service.ReviewWorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private RepositoryRepository repositoryRepository;
    private WebhookEventRepository webhookEventRepository;
    private WebhookSignatureValidator signatureValidator;
    private ReviewWorkerService reviewWorkerService;
    private EncryptionService encryptionService;
    private MockMvc mockMvc;

    private static final String REPO_URL = "https://github.com/laxman/demo-repo";
    private static final String PAYLOAD = """
            {"repository": {"html_url": "%s"}, "pull_request": {"number": 7}}
            """.formatted(REPO_URL);

    @BeforeEach
    void setUp() {
        repositoryRepository = mock(RepositoryRepository.class);
        webhookEventRepository = mock(WebhookEventRepository.class);
        signatureValidator = mock(WebhookSignatureValidator.class);
        reviewWorkerService = mock(ReviewWorkerService.class);
        encryptionService = mock(EncryptionService.class);
        // treat "encrypted" secrets as identity for these tests — only the
        // signature-validation call path is under test here, not encryption itself
        // (that's covered separately by EncryptionServiceTest)
        when(encryptionService.decrypt(anyString())).thenAnswer(invocation -> invocation.getArgument(0));

        WebhookController controller = new WebhookController(
                repositoryRepository, webhookEventRepository, signatureValidator, reviewWorkerService,
                encryptionService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new com.laxman.codereviewassistant.exception.GlobalExceptionHandler())
                .build();

        // JPA assigns the generated id on save() by mutating the passed entity —
        // this stub reproduces that behavior so the controller's event.getId() call works.
        when(webhookEventRepository.save(any(WebhookEvent.class))).thenAnswer(invocation -> {
            WebhookEvent event = invocation.getArgument(0);
            event.setId(99L);
            return event;
        });
    }

    @Test
    void unknownRepositoryIsRejectedBeforeSignatureIsEvenChecked() throws Exception {
        when(repositoryRepository.findByRepoUrl(REPO_URL)).thenReturn(Optional.empty());

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=irrelevant")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewWorkerService);
    }

    @Test
    void invalidSignatureIsRejected() throws Exception {
        Repository repo = repoFixture();
        when(repositoryRepository.findByRepoUrl(REPO_URL)).thenReturn(Optional.of(repo));
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=wrong")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(reviewWorkerService);
    }

    @Test
    void duplicateDeliveryIdIsAcknowledgedButNotReprocessed() throws Exception {
        Repository repo = repoFixture();
        when(repositoryRepository.findByRepoUrl(REPO_URL)).thenReturn(Optional.of(repo));
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(webhookEventRepository.findByDeliveryId("delivery-1"))
                .thenReturn(Optional.of(new WebhookEvent()));

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=valid")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(content().string("Already processed"));

        // the whole point of the idempotency check: a redelivered webhook
        // must never trigger a second, billable LLM review
        verifyNoInteractions(reviewWorkerService);
        verify(webhookEventRepository, never()).save(any());
    }

    @Test
    void newValidDeliveryIsSavedAndHandedToTheAsyncWorker() throws Exception {
        Repository repo = repoFixture();
        when(repositoryRepository.findByRepoUrl(REPO_URL)).thenReturn(Optional.of(repo));
        when(signatureValidator.isValid(anyString(), anyString(), anyString())).thenReturn(true);
        when(webhookEventRepository.findByDeliveryId("delivery-2")).thenReturn(Optional.empty());

        mockMvc.perform(post("/webhooks/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=valid")
                        .header("X-GitHub-Delivery", "delivery-2")
                        .content(PAYLOAD))
                .andExpect(status().isOk())
                .andExpect(content().string("Received"));

        verify(webhookEventRepository).save(any(WebhookEvent.class));
        verify(reviewWorkerService).processEvent(99L);
    }

    private Repository repoFixture() {
        Repository repo = new Repository();
        repo.setId(1L);
        repo.setRepoUrl(REPO_URL);
        repo.setWebhookSecret("shh");
        return repo;
    }
}
