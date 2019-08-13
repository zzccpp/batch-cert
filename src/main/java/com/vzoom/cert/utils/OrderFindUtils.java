package com.vzoom.cert.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author zhongchunping
 * @version 1.0
 * @Time 2019-07-29 10:54
 * @describe batch-cert
 *
 * 查找丢失订单
 */
public class OrderFindUtils {

    static String INFOTAG = "数据成功";//数据失败
    static Pattern patternNsrsbh = Pattern.compile("@推送\\[.*\\]"+INFOTAG);
    static Pattern patternOrder = Pattern.compile("@推送\\[.*\\]请求报文\\{(\\s.*\\s)*.*");
    static Set<String> nsrsbhSet = new HashSet<>();
    static Map<String,String> orderMap = new HashMap<>();

    public static void main(String[] args) throws Exception{

        File file = new File("D:/logs/cert/20190729");

        File[] files = null;
        if(file.isDirectory()){
            files = file.listFiles();
        }
        for (File fileTemp : files) {
            //读取文件
            System.out.println("读取文件:"+fileTemp.getName());
            loadFile(fileTemp);
        }
        if(orderMap.size()>0){
            System.out.println("--------------------------------------------------------------------------");
            String line;
            FileUtils.writeStringToFile(new File("D:/logs/order/order.txt"),"总共："+orderMap.size()+"单","utf-8",true);
            int count = 0;
            for (Map.Entry<String,String> entry : orderMap.entrySet()) {
                line = "\r\n"+(++count)+"-------------------"+entry.getKey()+"-------------------\r\n";
                FileUtils.writeStringToFile(new File("D:/logs/order/order.txt"),line,"utf-8",true);
                FileUtils.writeStringToFile(new File("D:/logs/order/order.txt"),entry.getValue(),"utf-8",true);
            }
            //FileUtils.writeStringToFile(new File("D:/logs/order/order.txt"),orderMap.values().toString(),"utf-8");
            System.out.println("找回订单已存入：order.txt文件中,总共："+orderMap.size()+"单");
        }
    }
    private static void loadFile(File file) throws Exception {
        String str = FileUtils.readFileToString(file, "UTF-8");
        Matcher matcher = patternNsrsbh.matcher(str);
        String nsrsbh;
        while(matcher.find()){
            nsrsbh = matcher.group().replace("@推送[", "").replace("]"+INFOTAG, "");
            nsrsbhSet.add(nsrsbh);
        }
        System.out.println(INFOTAG+"数量:"+nsrsbhSet.size());
        matcher = patternOrder.matcher(str);
        String order;
        while(matcher.find()){
            order = matcher.group();
            nsrsbh = order.substring(0,order.indexOf("]请求报文")).replace("@推送[","");
            if(nsrsbhSet.contains(nsrsbh)){
                order = order.substring(order.indexOf("请求报文"));
                if(order.startsWith("请求报文{\n"+"  \"data\"")){
                    order = StringUtils.substringBetween(order,"\"mqData\" : ",",\n"+"      \"queueName\"");
                    orderMap.put(nsrsbh+Math.random(),order);
                }else{
                    //@@推往网站的订单
                    System.out.println("@@往平台推送的订单>>"+nsrsbh);
                }
            }
        }
        System.out.println(INFOTAG+"订单数量:"+orderMap.size());
    }
}
