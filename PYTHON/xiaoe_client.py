# %%
from token_manager import TokenManager
import json
import requests

# 使用前请填写自己的凭据，切勿将真实凭据提交到仓库
APP_ID = "appXXXXXXXXXXXXX"
CLIENT_ID = "xopXXXXXXXXXXXXX"
CLIENT_SECRET = "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
GRANT_TYPE = "client_credential" # 固定值 硬编码即可

# %%

MANAGER = TokenManager(APP_ID, CLIENT_ID, CLIENT_SECRET, GRANT_TYPE)

# 调用小鹅client实现接口操作
class XiaoeClient:
    # user_params 需要传dict
    def request(self, method, url, user_params={}):
        access_token = MANAGER.token()
        user_params["access_token"] = access_token
        payload = json.dumps(user_params)
        headers = {
            'Content-Type': 'application/json'
        }
        response = requests.request(method, url, headers=headers, data=payload)
        return response.text
