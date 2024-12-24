package com.curd.main.service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.curd.main.model.DataEntity;
import com.curd.main.repo.DataRepository;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import jakarta.transaction.Transactional;

@Service
@Transactional
public class DataProcessor {
    private static final Logger logger = LoggerFactory.getLogger(DataProcessor.class);
    private final DataRepository dataRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final Executor taskExecutor;

    private final Timer processTimer = Metrics.globalRegistry.timer("data.process.time");
    private final Timer updateTimer = Metrics.globalRegistry.timer("data.update.time");
    private final Timer deleteTimer = Metrics.globalRegistry.timer("data.delete.time");

    public DataProcessor(DataRepository dataRepository, RedisTemplate<String, Object> redisTemplate, Executor taskExecutor) {
        this.dataRepository = dataRepository;
        this.redisTemplate = redisTemplate;
        this.taskExecutor = taskExecutor;
    }

    public String processData(String key) {
        return processTimer.record(() -> {
            try {
                Object cachedData = redisTemplate.opsForValue().get(key);
                if (cachedData != null) {
                    logger.info("Cache hit for key: {}", key);
                    return cachedData.toString();
                }

                Optional<DataEntity> dataEntity = dataRepository.findByKey1(key);
                if (dataEntity.isPresent()) {
                    String value = dataEntity.get().getValue1();
                    logger.info("Cache miss. Fetched from DB: key={}, value={}", key, value);

                    redisTemplate.opsForValue().set(key, value);
                    return value;
                } else {
                    logger.warn("No data found in database for key: {}", key);
                    return "Data not found.";
                }
            } catch (Exception e) {
                logger.error("Error processing data for key: {}", key, e);
                return "Error fetching data.";
            }
        });
    }

    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void updateData(String key, String value) {
        updateTimer.record(() -> {
            CompletableFuture.runAsync(() -> {
                synchronized (this) {
                    try {
                        Optional<DataEntity> dataEntity = dataRepository.findByKey1(key);
                        if (dataEntity.isPresent()) {
                            DataEntity entity = dataEntity.get();
                            entity.setValue1(value);
                            dataRepository.save(entity);
                        } else {
                            DataEntity newEntity = new DataEntity();
                            newEntity.setKey1(key);
                            newEntity.setValue1(value);
                            dataRepository.save(newEntity);
                        }
                        redisTemplate.opsForValue().set(key, value);
                        logger.info("Data updated for key: {}", key);
                    } catch (Exception e) {
                        logger.error("Error updating data for key: {}", key, e);
                    }
                }
            }, taskExecutor);
        });
    }

    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 2000))
    public void deleteData(String key) {
        deleteTimer.record(() -> {
            CompletableFuture.runAsync(() -> {
                synchronized (this) {
                    try {
                        Optional<DataEntity> dataEntity = dataRepository.findByKey1(key);
                        dataEntity.ifPresent(entity -> {
                            dataRepository.delete(entity);
                            logger.info("Data deleted for key: {}", key);
                        });

                        redisTemplate.delete(key);
                    } catch (Exception e) {
                        logger.error("Error deleting data for key: {}", key, e);
                    }
                }
            }, taskExecutor);
        });
    }
}
