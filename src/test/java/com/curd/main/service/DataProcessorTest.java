package com.curd.main.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;

import com.curd.main.model.DataEntity;
import com.curd.main.repo.DataRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.curd.main.model.DataEntity;
import com.curd.main.repo.DataRepository;

class DataProcessorTest {

    @Mock
    private DataRepository dataRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private Executor taskExecutor;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private DataProcessor dataProcessor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Replace default executor with a synchronous one for testing
        Executor synchronousExecutor = Runnable::run; // Executes tasks synchronously in the same thread
        dataProcessor = new DataProcessor(dataRepository, redisTemplate, synchronousExecutor);
    }
    @Test
    void testProcessData_CacheHit() {
        // Mock Redis cache hit
        when(valueOperations.get("testKey")).thenReturn("cachedValue");

        String result = dataProcessor.processData("testKey");

        // Verify
        verify(valueOperations, times(1)).get("testKey");
        assertEquals("cachedValue", result);
    }

    @Test
    void testProcessData_CacheMiss_DBHit() {
        // Mock Redis cache miss and DB hit
        when(valueOperations.get("testKey")).thenReturn(null);
        DataEntity entity = new DataEntity();
        entity.setKey1("testKey");
        entity.setValue1("dbValue");
        when(dataRepository.findByKey1("testKey")).thenReturn(Optional.of(entity));

        String result = dataProcessor.processData("testKey");

        // Verify
        verify(valueOperations, times(1)).get("testKey");
        verify(dataRepository, times(1)).findByKey1("testKey");
        verify(valueOperations, times(1)).set("testKey", "dbValue");
        assertEquals("dbValue", result);
    }

    @Test
    void testProcessData_NoData() {
        // Mock Redis cache miss and no DB data
        when(valueOperations.get("testKey")).thenReturn(null);
        when(dataRepository.findByKey1("testKey")).thenReturn(Optional.empty());

        String result = dataProcessor.processData("testKey");

        // Verify
        verify(valueOperations, times(1)).get("testKey");
        verify(dataRepository, times(1)).findByKey1("testKey");
        assertEquals("Data not found.", result);
    }

    @Test
    void testUpdateData() throws Exception {
        // Mock DB save and data fetch
        when(dataRepository.findByKey1("testKey")).thenReturn(Optional.empty());

        // Invoke the updateData method
        dataProcessor.updateData("testKey", "newValue");

        // Wait for async execution to complete
        Thread.sleep(500); // Adjust this based on your async execution timing

        // Verify async execution and DB save
        verify(dataRepository, times(1)).findByKey1("testKey");
        verify(dataRepository, times(1)).save(any(DataEntity.class));
        verify(valueOperations, times(1)).set("testKey", "newValue");
    }

    @Test
    void testDeleteData() throws Exception {
        // Mock DB find and delete
        DataEntity entity = new DataEntity();
        entity.setKey1("testKey");
        entity.setValue1("value");
        when(dataRepository.findByKey1("testKey")).thenReturn(Optional.of(entity));

        // Invoke the deleteData method
        dataProcessor.deleteData("testKey");

        // Wait for async execution to complete
        Thread.sleep(500); // Adjust this based on your async execution timing

        // Verify async execution, DB delete, and Redis delete
        verify(dataRepository, times(1)).findByKey1("testKey");
        verify(dataRepository, times(1)).delete(entity);
        verify(redisTemplate, times(1)).delete("testKey");
    }
}