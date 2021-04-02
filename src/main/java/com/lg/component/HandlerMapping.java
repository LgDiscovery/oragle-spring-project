package com.lg.component;

import java.lang.reflect.Method;
import java.util.Map;

public class HandlerMapping {
    private String requestUrl;
    private Object target;
    private Method method;
    private Map<Integer,String> methodParams;

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public Object getTarget() {
        return target;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public Map<Integer, String> getMethodParams() {
        return methodParams;
    }

    public void setMethodParams(Map<Integer, String> methodParams) {
        this.methodParams = methodParams;
    }
}
