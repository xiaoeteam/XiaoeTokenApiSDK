package com.xiaoe_tech.cloud.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class TokenManager {
    // access_token 有效期一般 7200 秒；剩余有效期 < 30 分钟时重新获取会返回新 Token，
    // 因此本地缓存提前 30 分钟过期，到期自动刷新
    private static final long REFRESH_AHEAD_MS = 30 * 60 * 1000;
    private static final String BASE_URL = "https://api.xiaoe-tech.com/stable_token";

    private String app_id = "";
    private String client_id = "";
    private String client_secret = "";
    private String grant_type = "";

    public TokenManager(String app_id, String client_id, String client_secret, String grant_type) {
        this.app_id = app_id;
        this.client_id = client_id;
        this.client_secret = client_secret;
        this.grant_type = grant_type;
    }

    public synchronized String getAccessToken() {
        String access_token = "";
        File file = new File("access_token.json");
        try {
            //文件不存在，则创建文件
            if (!file.exists()) file.createNewFile();
            //若文件为空，则第一次访问
            if (file.length() == 0) {
                access_token = this.refresh(file);
            } else {
                //从磁盘读取文件
                String json = this.readToken(file);
                JSONObject jsonObject = JSONObject.parseObject(json);
                if (jsonObject != null && jsonObject.get("expire_at") != null) {
                    long expireAt = jsonObject.getLongValue("expire_at");
                    long nowTime = System.currentTimeMillis();
                    //剩余有效期 > 30 分钟才复用缓存，否则刷新
                    if (expireAt - nowTime > REFRESH_AHEAD_MS) {
                        access_token = (String) jsonObject.get("access_token");
                    } else {
                        access_token = this.refresh(file);
                    }
                } else {
                    access_token = this.refresh(file);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return access_token;
    }

    //拉取新 token 并写入缓存文件
    private String refresh(File file) {
        JSONObject data = this.postForData();
        String access_token = data.getString("access_token");
        long expires_in = data.getLongValue("expires_in");
        HashMap<String, Object> map = new HashMap<>();
        map.put("access_token", access_token);
        map.put("expire_at", System.currentTimeMillis() + expires_in * 1000);
        this.writeToken(file, map);
        return access_token;
    }

    //手动强制刷新缓存
    public String refreshAccessToken() {
        File file = new File("access_token.json");
        if (file.exists()) file.delete();
        return getAccessToken();
    }

    //token持久化到磁盘
    private void writeToken(File file, Map map) {
        try {
            FileOutputStream fos = new FileOutputStream(file, false);
            String json = JSON.toJSONString(map);
            fos.write(json.getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //从磁盘读取token
    private String readToken(File file) {
        FileInputStream fis = null;
        StringBuilder json = new StringBuilder();
        try {
            fis = new FileInputStream(file);
            byte[] b = new byte[1024];
            int len;
            while ((len = fis.read(b)) != -1) {
                json.append(new String(b, 0, len));
            }
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return json.toString();
    }

    //发起 POST /stable_token 请求获取access_token（JSON body），失败自动重试一次
    private JSONObject postForData() {
        Map<String, String> params = new HashMap<>();
        params.put("app_id", this.app_id);
        params.put("client_id", this.client_id);
        params.put("client_secret", this.client_secret);
        params.put("grant_type", this.grant_type);
        String body = JSON.toJSONString(params);

        RuntimeException lastErr = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                URL reqURL = new URL(BASE_URL);
                HttpURLConnection conn = (HttpURLConnection) reqURL.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setDoOutput(true);
                conn.setDoInput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.close();

                InputStream is = conn.getInputStream();
                StringBuilder result = new StringBuilder();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();
                conn.disconnect();

                JSONObject jsonObject = JSONObject.parseObject(result.toString());
                if (jsonObject == null || jsonObject.getIntValue("code") != 0
                        || jsonObject.getJSONObject("data") == null
                        || jsonObject.getJSONObject("data").getString("access_token") == null) {
                    // 错误码/错误信息透传（如 2001 invalid secret_key、2026 新应用禁用 GET 等）
                    throw new RuntimeException("get access_token fail: " + result);
                }
                return jsonObject.getJSONObject("data");
            } catch (IOException e) {
                lastErr = new RuntimeException("request stable_token fail", e);
            }
        }
        throw lastErr;
    }
}
