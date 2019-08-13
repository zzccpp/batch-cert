package com.vzoom.cert.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author zhongchunping
 * @version 1.0
 * @Time 2019-08-02 11:24
 * @describe batch-cert <描述>
 */
@Controller
public class HelloController {

    @RequestMapping("/one")
    public String one(){

        return "redirect:http://localhost:9999/two";
    }

    @RequestMapping("/two")
    public String two(HttpServletRequest request){
        request.setAttribute("name","admin");
        System.out.println("跳转至JSP页面");
        return "index";
    }
}
