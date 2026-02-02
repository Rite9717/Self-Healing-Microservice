package com.project.ServiceA.Controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ServiceController
{
    private volatile boolean healthy=true;

    @GetMapping("/hello")
    public String hello()
    {
        if(!healthy)
        {
            throw new RuntimeException("Service is failing");
        }
        return "Hello from Service A";
    }

    @GetMapping("/fail")
    public String fail()
    {
        healthy=false;
        return "Service A is now failing";
    }

    @PostMapping("/recover")
    public String recover()
    {
        healthy=true;
        return "Service A recovered successfully";
    }
}
