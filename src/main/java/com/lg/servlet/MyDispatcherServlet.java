package com.lg.servlet;

import com.lg.annotation.*;
import com.lg.component.HandlerMapping;
import com.lg.config.MyConfig;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {

    private MyConfig myConfig = new MyConfig();

    private List<String> classNameList = new ArrayList<String>();

    private Map<String,Object> iocContainerMap = new HashMap<String, Object>();

    private Map<String, HandlerMapping> handlerMappingMap = new HashMap<String, HandlerMapping>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
       try {
           this.doDispatch(req,resp);
       }catch (Exception e){
           e.printStackTrace();
       }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp)throws Exception{
        String requestUrl = this.formatUrl(req.getRequestURI());
        HandlerMapping handlerMapping = handlerMappingMap.get(requestUrl);
        if(null == handlerMapping){
            resp.getWriter().write("404 Not Found");
            return;
        }
        Class<?>[] paramTypeArr = handlerMapping.getMethod().getParameterTypes();
        Object[] paramArr = new Object[paramTypeArr.length];
        for (int i=0;i<paramTypeArr.length;i++){
            Class<?> clazz = paramTypeArr[i];
            if(clazz == HttpServletRequest.class){
                paramArr[i] = req;
            }else if(clazz == HttpServletResponse.class){
                paramArr[i] = resp;
            }else if(clazz == String.class){
                Map<Integer, String> methodParams = handlerMapping.getMethodParams();
                paramArr[i] = req.getParameter(methodParams.get(i));
            }else{
                System.out.println("暂不支持的参数类型");
            }
        }
        handlerMapping.getMethod().invoke(handlerMapping.getTarget(),paramArr);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
       try {
           //1.加载配置文件
           doLoadConfig(config.getInitParameter("defaultConfig"));

       }catch (Exception e){
           System.out.println("加载配置文件失败");
           return;
       }
        //2.根据获取到的扫描路径进行扫描
       doScanPacakge(myConfig.getBasePackages());

       //3.将扫扫描到的类进行初始化，并存放在IOC容器中
        doInitializedClass();

        //4.依赖注入
        doDependcyInjection();

        System.out.println("DispatchServlet Init End...");

    }


    //依赖注入
    private void doDependcyInjection() {
        if(iocContainerMap.size()== 0){
            return;
        }
        Iterator<Map.Entry<String, Object>> iterator = iocContainerMap.entrySet().iterator();

        while(iterator.hasNext()){
            Map.Entry<String, Object> entry = iterator.next();
            Class<?> clazz = entry.getValue().getClass();
            Field[] fields = clazz.getDeclaredFields();
            //1.属性注入
            for (Field field:fields){
                //如果属性有WolfAutowired注解则注入值（暂时不考虑其他注解）
                if(field.isAnnotationPresent(WolfAutowired.class)){
                    String beanName = toLowerFirstLetterCase(field.getType().getSimpleName());
                    if(field.getType().isAnnotationPresent(WolfService.class)){
                        WolfService wolfService = field.getType().getAnnotation(WolfService.class);
                        beanName = wolfService.value();
                    }
                    field.setAccessible(true);

                    try{
                        Object target = iocContainerMap.get(beanName);
                        if(null == target){
                            System.out.println(clazz.getName() + " required bean:"+beanName+",but we not found it");
                        }
                        field.set(entry.getValue(),iocContainerMap.get(beanName));

                    }catch (IllegalAccessException e){
                        e.printStackTrace();
                    }
                }

                //2.初始化HandlerMapping
                String requestUrl = "";
                //获取Controller类上的请求路径
                if(clazz.isAnnotationPresent(WolfGetMapping.class)){
                    requestUrl = clazz.getAnnotation(WolfGetMapping.class).value();
                }
                //循环类中的方法，获取方法上的路径
                Method[] methods = clazz.getDeclaredMethods();
                for(Method method:methods){
                    //假设只有一种WolfGetMapping这一种注解
                    if(!method.isAnnotationPresent(WolfGetMapping.class)){
                        continue;
                    }
                    WolfGetMapping wolfGetMapping = method.getDeclaredAnnotation(WolfGetMapping.class);
                    requestUrl = requestUrl + "/"+ wolfGetMapping.value();
                   //不考虑正则匹配路径/***/的情况，只考虑完全匹配的情况
                    if(handlerMappingMap.containsKey(requestUrl)){
                        System.out.println("重复路径");
                        continue;
                    }
                    Annotation[][] annotationArr = method.getParameterAnnotations();

                    Map<Integer,String> methodParam = new HashMap<>();//储存参数的顺序和参数名
                    retryParam:
                    for(int i=0;i<annotationArr.length;i++){
                        for(Annotation annotation:annotationArr[i]){
                            if(annotation instanceof WolfRequestParam){
                                WolfRequestParam wolfRequestParam = (WolfRequestParam) annotation;
                               //存储参数的位置和注解中定义的参数名
                                methodParam.put(i,wolfRequestParam.value());
                                continue retryParam;
                            }
                        }
                    }
                    requestUrl = this.formatUrl(requestUrl);
                    HandlerMapping handlerMapping = new HandlerMapping();
                    handlerMapping.setRequestUrl(requestUrl);
                    handlerMapping.setMethod(method);
                    handlerMapping.setTarget(entry.getValue());
                    handlerMapping.setMethodParams(methodParam);
                    handlerMappingMap.put(requestUrl,handlerMapping);

                }
            }
        }
    }

    /**
     * 初始化类，并放入容器中
     */
    private void doInitializedClass() {
        if(classNameList.isEmpty()){
            return;
        }
        for (String className : classNameList){
            if(StringUtils.isEmpty(className)){
                return;
            }
            Class clazz;
            try{
                //反射获取类对象
                clazz = Class.forName(className);
                if(clazz.isAnnotationPresent(WolfController.class)){
                    String value = ((WolfController) clazz.getAnnotation(WolfController.class)).value();
                    //如果直接指定了value则取value,否则取首字母小写类名作为key值存储的实例对象
                    iocContainerMap.put(StringUtils.isBlank(value) ? toLowerFirstLetterCase(clazz.getSimpleName()):value,clazz.newInstance());
                }else if(clazz.isAnnotationPresent(WolfService.class)){
                    String value = ((WolfService) clazz.getAnnotation(WolfService.class)).value();
                    //如果直接指定了value则取value,否则取首字母小写类名作为key值存储的实例对象
                    iocContainerMap.put(StringUtils.isBlank(value) ? toLowerFirstLetterCase(clazz.getSimpleName()):value,clazz.newInstance());
                }else{
                    System.out.println("不考虑其他注解的情况");
                }
            }catch (Exception e){
                e.printStackTrace();
                System.out.println("初始化类失败，className为" + className);
            }
        }

    }

    /**
     * 将首字母装换为小写
     * @param className
     * @return
     */
    private String toLowerFirstLetterCase(String className) {
        if(StringUtils.isBlank(className)){
            return "";
        }
        String firstLetter = className.substring(0, 1);
        return firstLetter.toLowerCase()+ className.substring(1);
    }

    private void doScanPacakge(String basePackages) {
        if(StringUtils.isBlank(basePackages)){
            return;
        }
        String sacnPath = "/" + basePackages.replaceAll("\\.","/");
        URL url = this.getClass().getClassLoader().getResource(sacnPath);
        File files = new File(url.getFile());
        for(File file:files.listFiles()){
            if(file.isDirectory()){
                doScanPacakge(basePackages+"."+file.getName());
            }else{
                classNameList.add(basePackages+"."+file.getName().replace(".class",""));
            }
        }
    }

    private void doLoadConfig(String config) {
        InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(config);
        Properties properties = new Properties();
        try {
            properties.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("加载配置文件失败");
        }
        properties.forEach((k,v)->{
            try{
                Field field = myConfig.getClass().getDeclaredField((String) k);
                field.setAccessible(true);
                field.set(myConfig,v);
            }catch (Exception e){
                e.printStackTrace();
                System.out.println("初始化配置类失败");
                return;
            }
        });
    }

    private String formatUrl(String requestUrl) {
        requestUrl = requestUrl.replaceAll("/+", "/");
        if(requestUrl.lastIndexOf("/") == requestUrl.length()-1){
            requestUrl = requestUrl.substring(0,requestUrl.length()-1);
        }
        return requestUrl;
    }
}
