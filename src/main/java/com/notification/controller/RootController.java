package com.notification.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Root controller to serve the UI tester at the root path.
 */
@Controller
public class RootController {

    /**
     * Redirects root path to the static UI tester.
     */
    @GetMapping("/")
    public String index() {
        return "forward:/index.html";
    }
}