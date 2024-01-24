package com.learnkafka.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnkafka.domain.LibraryEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class LibraryEventsProducer {

    @Value("${spring.kafka.topic}")
    public String topic;

    private final KafkaTemplate<Integer, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    // 메세지 전송 방법 : 비동기
    public CompletableFuture<SendResult<Integer, String>> sendLibraryEvent(LibraryEvent libraryEvent) throws JsonProcessingException {
        var key = libraryEvent.libraryEventId();
        var value = objectMapper.writeValueAsString(libraryEvent);
        var completableFuture = kafkaTemplate.send(topic, key, value);

        //1.  blocking call - get metadata about the kafka cluster
        //2.  send message happens - Return a CompletableFuture
        return completableFuture // 미래에 완료될 결과를 가지고 있는 CompletableFuture 객체
                .whenComplete((sendResult, throwable) -> {
                    if (throwable != null) { // 실패
                        handleFailure(key, value, throwable);
                    } else { // 성공
                        handleSuccess(key, value, sendResult);
                    }
                });
    }

    // 메세지 전송 방법 : 동기
    public SendResult<Integer, String> sendLibraryEvent_approach2(LibraryEvent libraryEvent) throws JsonProcessingException, ExecutionException, InterruptedException, TimeoutException {
        var key = libraryEvent.libraryEventId();
        var value = objectMapper.writeValueAsString(libraryEvent);

        //1.  blocking call - get metadata about the kafka cluster
        //2.  Block and wait until the message is sent to the Kafka
        var sendResult = kafkaTemplate.send(topic, key, value)
//                .get();
        .get(3, TimeUnit.SECONDS);
        handleSuccess(key, value, sendResult);
        return sendResult;
    }

    // 권장 방법 (비동기 방식)
    // key 값을 보내는 대신에 객체를 생성
    public CompletableFuture<SendResult<Integer, String>> sendLibraryEvent_approach3(LibraryEvent libraryEvent) throws JsonProcessingException {
        var key = libraryEvent.libraryEventId();
        var value = objectMapper.writeValueAsString(libraryEvent);

        var producerRecord = buildProducerRecord(key, value);


        var completableFuture = kafkaTemplate.send(producerRecord);

        //1.  blocking call - get metadata about the kafka cluster
        //2.  send message happens - Return a CompletableFuture
        return completableFuture // 미래에 완료될 결과를 가지고 있는 CompletableFuture 객체
                .whenComplete((sendResult, throwable) -> {
                    if (throwable != null) { // 실패
                        handleFailure(key, value, throwable);
                    } else { // 성공
                        handleSuccess(key, value, sendResult);
                    }
                });
    }

    private ProducerRecord<Integer, String> buildProducerRecord(Integer key, String value) {
        List<Header> recordHeaders = List.of(new RecordHeader("event-source", "scanner".getBytes())); // 헤더도 보낼 수 있다.
        return new ProducerRecord<>(topic,null, key, value, recordHeaders);
    }

    private void handleSuccess(Integer key, String value, SendResult<Integer, String> sendResult) {
        log.info("Message sent successfully for the key : {} and the value is {} , partition is {}", key, value, sendResult.getRecordMetadata().partition());
    }

    private void handleFailure(Integer key, String value, Throwable ex) {
        log.error("Error sending the message and the exception is {}", ex.getMessage(), ex);
    }
}
