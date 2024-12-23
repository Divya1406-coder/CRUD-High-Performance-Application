package com.curd.main.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.curd.main.model.DataEntity;
import com.curd.main.service.DataProcessor;

@RestController
@RequestMapping("/api/data")
public class CrudController {

    @Autowired
    private DataProcessor dataProcessor;

    @PostMapping
    public String create(@RequestBody DataEntity data) {
        dataProcessor.updateData(data.getKey1(), data.getValue1());
        return "Data created successfully.";
    }

    @GetMapping    
    public String read(@RequestParam String key) {
        return dataProcessor.processData(key); 
    }

    @PutMapping
    public String update(@RequestParam String key, @RequestParam String value) {
        dataProcessor.updateData(key, value);
        return "Data updated successfully.";
    }

    @DeleteMapping
    public String delete(@RequestParam String key) {
        dataProcessor.deleteData(key);
        return "Data deleted successfully.";
    }
}
