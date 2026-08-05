package com.laxman.codereviewassistant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/repos-page")
    public String repos() {
        return "repos";
    }

    @GetMapping("/review-page")
    public String review() {
        return "review";
    }
}