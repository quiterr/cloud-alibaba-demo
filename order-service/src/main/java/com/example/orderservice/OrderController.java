package com.example.orderservice;

import com.example.common.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private UserFeignClient userFeignClient;

    @GetMapping("/getUser/{id}")
    public User getUser(@PathVariable Long id) {
        return userFeignClient.getUserById(id);
    }
}