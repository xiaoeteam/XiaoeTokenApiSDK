## 快速开始 & demo

本目录为 Go 版 SDK：`POST /stable_token` 获取并自动维护 access_token（进程内文件缓存，剩余有效期 < 30 分钟自动刷新，失败重试一次）。

```go
const (
XiaoEAppId     = "appxxxx" // 店铺app_id 
XiaoEClientId  = "xxxxxx"  // 店铺client_id
XiaoEAppSecret = "xxxxxx"  // 店铺client_secret
XiaoEGrantType = "client_credential" // 固定不变

GetUserListOpenApi = "https://api.xiaoe-tech.com/xe.user.batch.get/1.0.0"
)

func main() {
xiaoE := &DefaultAccessTokenManager{
Id:   XiaoEAppId,
Name: "access_token",
GetRefreshRequestFunc: func() *http.Request {
params := make(map[string]string)
params["app_id"] = XiaoEAppId
params["client_id"] = XiaoEClientId
params["client_secret"] = XiaoEAppSecret
params["grant_type"] = XiaoEGrantType

str, err := json.Marshal(params)
if err != nil {
fmt.Println(err)
}
payload := strings.NewReader(string(str))
// POST /stable_token，JSON body
req, err := http.NewRequest(http.MethodPost, ServerUrl, payload)
if err != nil {
return nil
}

return req
},
Cache: file.New(os.TempDir()),
}

// 小鹅云 客户端
xiaoEClient := NewClient(xiaoE)


// 调用示例 不需要再次传入access_token
UserListParams := make(map[string]interface{})
UserListParams["page"] = 1
UserListParams["page_size"] = 2

resp, err := xiaoEClient.CurlDo(http.MethodPost, GetUserListOpenApi, UserListParams)
if err != nil {
fmt.Println("err: ", err)
}
fmt.Println(string(resp))
}
```
