package io.github.angelogalvao.kafka.producer;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;

public class Main {
    public static void main(String[] args) throws Throwable {
        
        
        Properties properties = new Properties();

        try (InputStream propStream = Files.newInputStream(Paths.get("config.properties"))) {
            properties.load(propStream);
        }
        
        KafkaProducer<String, String> consumer = new KafkaProducer<>(properties);
    }
}