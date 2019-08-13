package com.vzoom.cert.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.vzoom.cert.bean.CertInfo;
import com.vzoom.cert.utils.Encodes;
import com.vzoom.cert.utils.HttpClientUtil;
import com.vzoom.cert.utils.RSACrypt;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhongchunping
 * @version 1.0
 * @Time 2019-07-03 11:35
 * @describe batch-cert
 *
 * 深圳批量认证
 */
@Controller
@RequestMapping("/sz")
public class SZBatchCertController {

    private static final Logger logger = Logger.getLogger(SZBatchCertController.class);

    @Value("${proCode}")
    private String proCode;
    @Value("${cityCode}")
    private String cityCode;
    @Value("${isPushData}")
    private String isPushData;
    @Value("${filePath}")
    private String filePath;
    @Value("${appId}")
    private String appId;
    @Value("${privateKey}")
    private String privateKey;
    @Value("${publicKey}")
    private String publicKey;
    @Value("${certUri}")
    private String certUri;

    @RequestMapping("/batchCert.do")
    @ResponseBody
    public String doBatchCert(){
        //检查参数是否传值
        checkParam();
        //从excel中读取需要授权的纳人信息
        ConcurrentHashMap<String, CertInfo> certInfos = readNsrInfos(filePath);
        StringBuilder msg = new StringBuilder("总共有["+certInfos.size()+"]条记录需要处理,");
        //循环调用认证极简认证接口
        String certUrl;
        String result;
        boolean flag;
        for (Map.Entry<String,CertInfo> entry : certInfos.entrySet()) {
            //拼装极简认证地址
            certUrl = createUrl(entry.getValue());
            if(StringUtils.isBlank(certUrl))break;
            //调用认证
            result = HttpClientUtil.httpGet(certUrl, null);
            //检查认证结果,认证成功则删除Map中数据
            flag = checkCertResult(result);
            if(flag) certInfos.remove(entry.getKey());
        }
        msg.append("还剩["+certInfos.size()+"]未处理完!");
        for (Map.Entry<String,CertInfo> entry : certInfos.entrySet()) {
            msg.append(entry.getKey()+",");
        }
        return msg.toString();
    }

    /**
     * 校验是否认证成功
     * @param result
     * @return
     */
    private boolean checkCertResult(String result) {
        try {
            logger.info("认证返回:"+result);
            String data = result.replace("%2B","+");
            String certResult = RSACrypt.decryptByPrivateKey(data, privateKey);
            logger.info("解密后:"+certResult);
            String code = JSON.parseObject(certResult).getString("code");
            return "10000".equals(code);
        } catch (Exception e) {
            //e.printStackTrace();//不建议这么写
            logger.error("解密认证返回报文异常!", e);
        }
        return false;
    }

    /**
     * 拼装认证地址
     * @param info
     */
    private String createUrl(CertInfo info) {
        try {
            Map<String,String> mapContent = new HashMap<>();
            mapContent.put("proCode", proCode);
            mapContent.put("cityCode", cityCode);
            mapContent.put("ver", "mo");
            mapContent.put("is_redirect", "N");
            mapContent.put("redirect_uri", URLEncoder.encode("http://www.baidu.com","UTF-8"));
            mapContent.put("is_push_data", isPushData);//Y or N--->Y需要推送数据;N不需要推送数据,一般为N,有需要推送数据需求需联络配置
            mapContent.put("fromVer", "mo");
            //填充值
            mapContent.put("nsrsbh",info.getNsrsbh());
            mapContent.put("certCode",info.getCertCode());
            mapContent.put("companyName",info.getNsrmc());
            //封装vz_content参数内容
            String vz_content = JSON.toJSONString(mapContent);
            vz_content = new String(new Base64().encode(vz_content.getBytes("UTF-8")));
            vz_content = vz_content.replace("+", "%2B");
            //签名
            String sign = RSACrypt.signByPrivateKey(vz_content,privateKey,"UTF-8");
            //System.out.println("签名后的长度:"+ Encodes.decodeBase64(sign).length);
            sign = sign.replace("+", "%2B");
            // 认证地址
            String certUrl = certUri+"/invoke.html?reType=json&app_id="+appId+"&vz_content="+vz_content+"&sign="+sign;
            logger.error("拼装认证地址:"+info.getNsrsbh()+"<>"+certUrl);
            return certUrl;
        } catch (Exception e) {
            logger.error("载入数据异常:"+info.getNsrsbh()+"拼装认证地址异常!"+e);
        }
        return null;
    }

    /**
     * 读取poi中数据
     * @param filePath
     * @return
     */
    private ConcurrentHashMap<String, CertInfo> readNsrInfos(String filePath) {

        ConcurrentHashMap<String,CertInfo> certInfoMaps = new ConcurrentHashMap<>();
        try {
            //1、获取文件输入流
            //String filePath = SZBatchCertController.class.getResource("../../../../"+fileName).getPath();
            FileInputStream inputStream = new FileInputStream(filePath);
            //2、获取Excel工作簿对象
            HSSFWorkbook workbook = new HSSFWorkbook(inputStream);
            //3、得到Excel工作表对象
            HSSFSheet sheetAt = workbook.getSheetAt(0);
            int lastRowNum = sheetAt.getLastRowNum();
            System.out.println(lastRowNum);
            logger.info("读取文件["+filePath+"]中共有:["+lastRowNum+"]行数据");
            String nsrsbh;
            String nsrmc;
            String certCode;
            HSSFRow row;
            CertInfo certInfo;
            for(int i=1;i <= lastRowNum;i++) {
                row = sheetAt.getRow(i);
                certInfo = new CertInfo();
                nsrsbh = row.getCell(0).getStringCellValue();
                certInfo.setNsrsbh(nsrsbh);
                nsrmc = row.getCell(1).getStringCellValue();
                if(StringUtils.isBlank(nsrmc)){
                    logger.info(nsrsbh+"对应nsrmc为空,不添加!");
                    continue;
                }
                certInfo.setNsrmc(nsrmc);
                certCode = row.getCell(2).getStringCellValue();
                if(StringUtils.isBlank(certCode)){
                    logger.info(nsrsbh+"对应certCode为空,不添加!");
                    continue;
                }
                certInfo.setCertCode(certCode);
                certInfoMaps.put(nsrsbh+Math.random(),certInfo);
            }
            logger.info("已读取完文件,共载入:["+certInfoMaps.size()+"]条记录");
        } catch (IOException e) {
            logger.error("载入数据异常:"+e);
        }
        return certInfoMaps;
    }

    /**
     * 检查初始化参数是否都有值
     */
    private void checkParam() {
        if(StringUtils.isBlank(proCode))logger.info(">>>ERROR<<<未指定proCode.");
        if(StringUtils.isBlank(cityCode))logger.info(">>>ERROR<<<未指定cityCode.");
        if(StringUtils.isBlank(isPushData))logger.info(">>>ERROR<<<未指isPushData.");
        if(StringUtils.isBlank(filePath))logger.info(">>>ERROR<<<未指定读取文件.");
        if(StringUtils.isBlank(appId))logger.info(">>>ERROR<<<未指定appId.");
        logger.info(">>>>>>"+appId);
        if(StringUtils.isBlank(privateKey))logger.info(">>>ERROR<<<未指定privateKey.");
        logger.info(">>>>>>"+privateKey);
        if(StringUtils.isBlank(publicKey))logger.info(">>>ERROR<<<未指定publicKey.");
        if(StringUtils.isBlank(certUri))logger.info(">>>ERROR<<<未指定certUri.");
    }
    /*public static void main(String[] args) {
        new SZBatchCertController().doBatchCert();//readNsrInfos("szcert.xls");

    }*/
}
