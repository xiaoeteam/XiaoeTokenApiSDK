# XiaoeTokenApiSDK

小鹅云稳定 token 获取的 SDK —— 只做一件事：调用 `POST https://api.xiaoe-tech.com/stable_token` 获取并维护 `access_token`。

> 版本：v1.0.0
>
> 旧接口 `GET /token` 的 SDK 见 [XiaoeApiSDK](https://github.com/xiaoeteam/XiaoeApiSDK)，本 SDK 不覆盖其功能。

## 为什么用 /stable_token

- `POST /stable_token` 是获取 access_token 的**推荐方式**，密钥走 JSON body，不进 URL；
- 新应用将**不再支持**通过 `GET /token` 获取 access_token（错误码 2026）。

## 快速开始

各语言目录内含可运行 demo，使用前把凭据换成自己的（**切勿提交真实凭据**）。

### Go（[GO](GO)）

```go
const (
    XiaoEAppId     = "appxxxx"           // 店铺app_id
    XiaoEClientId  = "xxxxxx"            // 应用的client_id
    XiaoEAppSecret = "xxxxxx"            // 应用的凭证秘钥，即client_secret
    XiaoEGrantType = "client_credential" // 固定不变
)

xiaoE := &demo.DefaultAccessTokenManager{
    Id:   XiaoEAppId,
    Name: "access_token",
    GetRefreshRequestFunc: func() *http.Request {
        params := map[string]string{
            "app_id":        XiaoEAppId,
            "client_id":     XiaoEClientId,
            "secret_key":    XiaoEAppSecret,
            "grant_type":    XiaoEGrantType,
        }
        str, _ := json.Marshal(params)
        req, _ := http.NewRequest(http.MethodPost, demo.ServerUrl, strings.NewReader(string(str)))
        return req
    },
    Cache: file.New(os.TempDir()),
}
```

### Java（[JAVA](JAVA)）

```java
TokenManager tokenManager = new TokenManager(app_id, client_id, secret_key, "client_credential");
String accessToken = tokenManager.getAccessToken(); // 自动缓存与刷新
```

### PHP（[PHP](PHP)）

```php
require_once __DIR__ . '/libs/TokenManage.php';
$tokenManage = new TokenManage(); // 填好 TokenManage 内的凭据
$accessToken = $tokenManage->getToken();
```

### Python（[PYTHON](PYTHON)）

```python
from token_manager import TokenManager
manager = TokenManager(APP_ID, CLIENT_ID, SECRET_KEY, "client_credential")
access_token = manager.token()  # 自动缓存与刷新
```

### curl 示例

```sh
curl -X POST https://api.xiaoe-tech.com/stable_token \
  -H "Content-Type: application/json" \
  -d '{"app_id":"appxxxx","client_id":"xopxxxx","secret_key":"xxxxxx","grant_type":"client_credential"}'
```

## 接入步骤

1. 在云服务控制台创建应用，拿到 `app_id` / `client_id` / `secret_key`，并把服务器 IP 加入白名单；
2. 引入对应语言目录的代码，填入凭据；
3. 调用 token 方法获取 `access_token`（SDK 自动缓存，到期重新获取；如需失败重试可在获取请求处自行增加一次重试）；
4. 拿 `access_token` 调用业务 API。

## 接口约定

- 请求：`POST https://api.xiaoe-tech.com/stable_token`，`Content-Type: application/json`
- 参数：`app_id`（必填）、`client_id`（必填）、`secret_key`（必填，即 client_secret）、`grant_type`（`client_credential`）
- 响应：`{"code":0,"msg":"success","data":{"access_token":"...","expires_in":7200}}`

## 错误码

| code | 说明 |
|---|---|
| 0 | 成功 |
| 2001 | 密钥错误（invalid secret_key） |
| 2002 | app_id 错误 |
| 2020 | 请求过于频繁（10分钟100次限频） |
| 2026 | 新应用不再支持通过 GET /token 获取 access_token，请改用 POST /stable_token |
| 2051 | IP 不在白名单 |

完整错误码见[全局返回码](https://api-doc.xiaoe-tech.com/common_problem/code.html)。

## 安全须知

- `secret_key` / `access_token` 不写入日志，不硬编码在代码里提交到仓库；
- token 缓存文件（如 `access_token.json`、`*_access_token.json`）已在 `.gitignore` 中排除，禁止提交。
