<?php
require_once __DIR__ . '/Http.php';

class TokenManage
{
    const URL = "https://api.xiaoe-tech.com/stable_token";
    // 提前 30 分钟刷新：剩余有效期 < 30 分钟时重新获取会返回新 Token
    const REFRESH_AHEAD = 1800;

    // 使用前请填写自己的凭据，切勿将真实凭据提交到仓库
    private $app_id = "appXXXXXXXXXX";
    private $client_id = "xopXXXXXXXXXX";
    private $client_secret = "XXXXXXXXXX";
    private $grant_type = "client_credential";


    public function getToken()
    {
        $filePath = dirname(__DIR__) . DIRECTORY_SEPARATOR . $this->app_id . "_access_token.json";
        if(file_exists($filePath)){
           return $this->getFile($filePath);
        }else{
            return $this->requestToken();
        }

    }

    public function requestToken(){
        $params = [
            "app_id" => $this->app_id,
            "client_id" => $this->client_id,
            "client_secret" => $this->client_secret,
            "grant_type" => $this->grant_type
        ];
        // POST /stable_token，JSON body；失败自动重试一次
        $result = Http::curlPost(self::URL, array(), $params, array(), 5);
        if (empty($result['body']) || !isset($result['body']['code']) || $result['body']['code'] != 0) {
            // 重试一次
            $result = Http::curlPost(self::URL, array(), $params, array(), 5);
        }

        if (!empty($result['body']) && isset($result['body']['code']) && $result['body']['code'] == 0) {
            $this->writeFile($result['body']['data']);
            return $result['body']['data']['access_token'];
        }
        // 错误码/错误信息透传（如 2001 invalid secret_key、2026 新应用禁用 GET 等）
        return false;
    }

    public function writeFile($data)
    {
        $file = fopen($this->app_id . "_access_token.json", "w") or die("Unable to open file!");
        $fileData = [
            "access_token" => $data['access_token'],
            // 提前 30 分钟过期重取
            "expires" => time() + $data['expires_in'] - self::REFRESH_AHEAD
        ];
        fwrite($file, json_encode($fileData));
        fclose($file);
    }

    public function getFile($filePath){
        $json = file_get_contents($filePath);
        $tokenInfo = json_decode($json, true);
        if($tokenInfo['expires'] > time()) {
            return $tokenInfo['access_token'];
        }else{
            return $this->requestToken();
        }
    }
}
