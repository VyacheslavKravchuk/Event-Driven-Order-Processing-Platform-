package com.inovexx.notification_service.service;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

public interface NotificationService {

    ResponseEntity<String> sendNotification(@PathVariable String userId);
}
