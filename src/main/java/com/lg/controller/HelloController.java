package com.lg.controller;

import com.lg.annotation.WolfAutowired;
import com.lg.annotation.WolfController;
import com.lg.annotation.WolfGetMapping;
import com.lg.annotation.WolfRequestParam;
import com.lg.service.HelloService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WolfController
public class HelloController {

    @WolfAutowired
    private HelloService helloService;

    @WolfGetMapping(value = "/hello")
    public void query(HttpServletRequest request, HttpServletResponse response
    , @WolfRequestParam(value = "name")String name) throws IOException {
        response.setContentType("text/html;charset=utf-8");
        response.getWriter().write("Hello:"+name);
    }
}
