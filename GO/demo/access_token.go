package demo

import (
	"encoding/json"
	"fmt"
	"github.com/faabiosr/cachego"
	"io/ioutil"
	"net/http"
	"net/url"
	"strings"
	"sync"
	"time"
)

type AccessTokenManager interface {
	GetName() (name string)
	GetAccessToken() (accessToken string, err error)
}

type getRefreshRequestFunc func() *http.Request

type DefaultAccessTokenManager struct {
	Id                    string
	Name                  string
	GetRefreshRequestFunc getRefreshRequestFunc
	Cache                 cachego.Cache
}

// 防止多个 goroutine 并发刷新冲突
var getAccessTokenLock sync.Mutex

// GetAccessToken 获取access_token
func (m *DefaultAccessTokenManager) GetAccessToken() (accessToken string, err error) {
	cacheKey := m.getCacheKey()
	accessToken, err = m.Cache.Fetch(cacheKey)
	if accessToken != "" {
		return
	}

	getAccessTokenLock.Lock()
	defer getAccessTokenLock.Unlock()

	accessToken, err = m.Cache.Fetch(cacheKey)
	if accessToken != "" {
		return
	}

	req := m.GetRefreshRequestFunc()
	// 添加 serverUrl
	if !strings.HasPrefix(req.URL.String(), "http") {
		parse, _ := url.Parse(ServerUrl)
		req.URL.Host = parse.Host
		req.URL.Scheme = parse.Scheme
	}

	req.Header.Set("Content-Type", contentTypeApplicationJson)

	// 请求失败重试一次（重建请求，避免 body 已被读取）
	var resp []byte
	for attempt := 0; attempt < 2; attempt++ {
		resp, err = doTokenRequest(req)
		if err == nil {
			break
		}
		if attempt == 0 {
			req = m.GetRefreshRequestFunc()
			if !strings.HasPrefix(req.URL.String(), "http") {
				parse, _ := url.Parse(ServerUrl)
				req.URL.Host = parse.Host
				req.URL.Scheme = parse.Scheme
			}
			req.Header.Set("Content-Type", contentTypeApplicationJson)
		}
	}
	if err != nil {
		return
	}

	var result = struct {
		Code int    `json:"code"`
		Msg  string `json:"msg"`
		Data struct {
			AccessToken string  `json:"access_token"`
			ExpiresIn   float64 `json:"expires_in"`
		} `json:"data"`
	}{}

	err = json.Unmarshal(resp, &result)
	if err != nil {
		err = fmt.Errorf("unmarshal error %s", string(resp))
		return
	}

	if result.Data.AccessToken == "" {
		err = fmt.Errorf("%s", string(resp))
		return
	}

	accessToken = result.Data.AccessToken

	// 提前 30 分钟过期重取：剩余有效期 < 30 分钟时重新获取将返回新 Token
	ttl := time.Duration(result.Data.ExpiresIn-1800) * time.Second
	if ttl < time.Minute {
		ttl = time.Minute
	}
	err = m.Cache.Save(cacheKey, accessToken, ttl)
	if err != nil {
		return
	}

	return
}

// doTokenRequest 执行一次 token 请求并读取响应体
func doTokenRequest(req *http.Request) (resp []byte, err error) {
	response, err := http.DefaultClient.Do(req)
	if err != nil {
		return
	}
	defer response.Body.Close()
	resp, err = ioutil.ReadAll(response.Body)
	return
}

// getCacheKey
func (m *DefaultAccessTokenManager) getCacheKey() (key string) {
	return "access_token:" + m.Id
}

// GetName 获取 access_token 参数名称
func (m *DefaultAccessTokenManager) GetName() (name string) {
	return m.Name
}
