package com.laxman.codereviewassistant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.laxman.codereviewassistant.entity.WebhookEvent;


public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByDeliveryId(String deliveryId);

}
