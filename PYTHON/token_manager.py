# %%
import requests
import json
import os.path
from datetime import datetime, timedelta
# %%
BASE_URL = 'https://api.xiaoe-tech.com'
TOKEN_PATH = '/stable_token'

TOKEN_CACHE_PATH = 'access_token.json'

# 提前 30 分钟刷新：剩余有效期 < 30 分钟时重新获取会返回新 Token
REFRESH_AHEAD = timedelta(seconds=1800)

# %%

# TokenManager类维护token功能


class TokenManager:
    def __init__(self, app_id, client_id, client_secret, grant_type):
        self.app_id = app_id
        self.client_id = client_id
        self.client_secret = client_secret
        self.grant_type = grant_type
        self.access_token = None
        self.token_expire_at = None

    # 外部调用token方法拿access token
    def token(self):
        self._read_token()
        if not self.access_token or not self.token_expire_at or self.token_expire_at - REFRESH_AHEAD < datetime.now():
            ok, msg = self._get_access_token()
            if not ok:
                raise Exception("get access token fail:"+msg)
            self._write_token()
        return self.access_token

    # 请求小鹅通获取access token（POST /stable_token，JSON body），失败自动重试一次
    def _get_access_token(self):
        payload = json.dumps({
            "app_id": self.app_id,
            "client_id": self.client_id,
            "client_secret": self.client_secret,
            "grant_type": self.grant_type,
        })
        headers = {
            'Content-Type': 'application/json'
        }
        last_err = None
        for _ in range(2):
            try:
                response = requests.post(BASE_URL + TOKEN_PATH,
                                         headers=headers, data=payload)
            except requests.RequestException as e:
                last_err = e
                continue
            if not response.ok:
                last_err = Exception("request fail, http status: %s" % response.status_code)
                continue
            resp = json.loads(response.text)
            if resp['code'] != 0 or not resp['data']['access_token']:
                # 错误码/错误信息透传（如 2001 invalid secret_key、2026 新应用禁用 GET 等）
                return False, resp['msg']
            self.access_token = resp['data']['access_token']
            self.token_expire_at = datetime.now(
            ) + timedelta(seconds=resp['data']['expires_in'])
            return True, resp['msg']
        return False, str(last_err)

    # 从磁盘读取token
    def _read_token(self):
        if not self.access_token and os.path.isfile(self.app_id + '_' + TOKEN_CACHE_PATH):
            token_dict = {}
            with open(self.app_id + '_' + TOKEN_CACHE_PATH, 'r') as f:
                token_dict = json.load(f)
            if token_dict['access_token']:
                self.access_token = token_dict['access_token']
                self.token_expire_at = datetime.fromtimestamp(
                    token_dict['token_expire_at'])

    # token持久化到磁盘
    def _write_token(self):
        with open(self.app_id + '_' + TOKEN_CACHE_PATH, 'w') as f:
            json.dump({
                "access_token": self.access_token,
                "token_expire_at": datetime.timestamp(self.token_expire_at),
            }, f)
