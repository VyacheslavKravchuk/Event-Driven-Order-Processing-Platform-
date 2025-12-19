package com.inovexx.notification_service.config.send;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import javax.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;

@Configuration
public class FirebaseConfig {

    @Value("classpath:firebase-adminsdk.json") // Путь к вашему JSON-файлу
    Resource serviceAccount;

    @PostConstruct
    public void initialize() throws IOException {
        FileInputStream serviceAccountStream = new FileInputStream(serviceAccount.getFile());

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccountStream))
                // .setDatabaseUrl("https://your-project-id.firebaseio.com") // Опционально, если используете Realtime DB
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}
