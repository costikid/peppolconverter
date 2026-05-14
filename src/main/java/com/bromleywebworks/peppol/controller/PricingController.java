package com.bromleywebworks.peppol.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PricingController {

    @GetMapping("/pricing")
    public String pricing(Model model) {
        model.addAttribute("title", "Pricing - Peppol Converter");
        model.addAttribute("description", "Choose the right plan for your Peppol conversion needs");
        return "pricing";
    }
}
