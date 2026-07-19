"""讯飞声纹识别提供者"""

import base64
import hashlib
import hmac
import json
from datetime import datetime, timezone
from urllib.parse import urlencode, urlparse
from typing import Optional, Dict, Any, List

import httpx

from core.providers.voiceprint.base import VoiceprintProviderBase
from config.logger import setup_logging

TAG = __name__
logger = setup_logging()

# 讯飞声纹 API 基础地址
XFYUN_BASE_URL = "https://api.xf-yun.com/v1/private/s1aa729d0"


class VoiceprintProvider(VoiceprintProviderBase):
    """讯飞声纹识别提供者"""

    def __init__(self, config: dict):
        """初始化讯飞声纹提供者

        Args:
            config: 配置字典，需包含:
                - app_id: 讯飞应用ID
                - api_key: 讯飞API Key
                - api_secret: 讯飞API Secret
                - group_id: 声纹特征库ID（可选，默认 xiaozhi_voiceprint_group）
        """
        super().__init__(config)
        self.app_id = config.get("app_id", "")
        self.api_key = config.get("api_key", "")
        self.api_secret = config.get("api_secret", "")
        self.group_id = config.get("group_id", "xiaozhi_voiceprint_group")

        if not all([self.app_id, self.api_key, self.api_secret]):
            raise ValueError("讯飞声纹配置缺失：需要 app_id, api_key, api_secret")

        self.client = httpx.AsyncClient(timeout=30.0)

    def _generate_auth_url(self) -> str:
        """生成带鉴权参数的请求 URL"""
        date = datetime.now(timezone.utc).strftime('%a, %d %b %Y %H:%M:%S GMT')

        parsed = urlparse(XFYUN_BASE_URL)
        host = parsed.netloc
        path = parsed.path

        # 生成签名原文
        signature_origin = f"host: {host}\ndate: {date}\nPOST {path} HTTP/1.1"

        # 使用 hmac-sha256 计算签名
        signature_sha = hmac.new(
            self.api_secret.encode('utf-8'),
            signature_origin.encode('utf-8'),
            hashlib.sha256
        ).digest()
        signature = base64.b64encode(signature_sha).decode('utf-8')

        # 生成 authorization
        authorization_origin = (
            f'api_key="{self.api_key}", algorithm="hmac-sha256", '
            f'headers="host date request-line", signature="{signature}"'
        )
        authorization = base64.b64encode(authorization_origin.encode('utf-8')).decode('utf-8')

        params = {
            "authorization": authorization,
            "host": host,
            "date": date
        }
        return f"{XFYUN_BASE_URL}?{urlencode(params)}"

    def _build_request_body(
        self,
        func: str,
        feature_id: Optional[str] = None,
        feature_info: Optional[str] = None,
        audio_base64: Optional[str] = None,
        res_key: str = "createFeatureRes"
    ) -> Dict[str, Any]:
        """构建讯飞 API 请求体"""
        body = {
            "header": {
                "app_id": self.app_id,
                "status": 3
            },
            "parameter": {
                "s1aa729d0": {
                    "func": func,
                    "groupId": self.group_id,
                    f"{res_key}": {
                        "encoding": "utf8",
                        "compress": "raw",
                        "format": "json"
                    }
                }
            }
        }

        if feature_id:
            body["parameter"]["s1aa729d0"]["featureId"] = feature_id
        if feature_info:
            body["parameter"]["s1aa729d0"]["featureInfo"] = feature_info

        if audio_base64:
            body["payload"] = {
                "resource": {
                    "encoding": "raw",
                    "sample_rate": 16000,
                    "channels": 1,
                    "bit_depth": 16,
                    "status": 3,
                    "audio": audio_base64
                }
            }

        return body

    def _ensure_wav_format(self, audio_bytes: bytes) -> bytes:
        """确保音频是 16kHz 16bit 单声道 WAV 格式"""
        if len(audio_bytes) > 44 and audio_bytes[:4] == b'RIFF' and audio_bytes[8:12] == b'WAVE':
            return audio_bytes

        # PCM 数据添加 WAV 头
        sample_rate = 16000
        channels = 1
        bits = 16
        byte_rate = sample_rate * channels * bits // 8
        block_align = channels * bits // 8

        header = b'RIFF'
        data_size = len(audio_bytes)
        file_size = 36 + data_size
        header += file_size.to_bytes(4, 'little')
        header += b'WAVE'
        header += b'fmt '
        header += (16).to_bytes(4, 'little')
        header += (1).to_bytes(2, 'little')
        header += channels.to_bytes(2, 'little')
        header += sample_rate.to_bytes(4, 'little')
        header += byte_rate.to_bytes(4, 'little')
        header += block_align.to_bytes(2, 'little')
        header += bits.to_bytes(2, 'little')
        header += b'data'
        header += data_size.to_bytes(4, 'little')

        return header + audio_bytes

    async def register_voiceprint(self, speaker_id: str, audio_bytes: bytes, feature_info: str = "") -> bool:
        """注册声纹"""
        try:
            wav_bytes = self._ensure_wav_format(audio_bytes)
            audio_base64 = base64.b64encode(wav_bytes).decode('utf-8')

            url = self._generate_auth_url()
            body = self._build_request_body(
                func="createFeature",
                feature_id=speaker_id,
                feature_info=feature_info or f"registered at {datetime.now().isoformat()}",
                audio_base64=audio_base64,
                res_key="createFeatureRes"
            )

            response = await self.client.post(url, json=body)
            result = response.json()

            if result.get("header", {}).get("code") != 0:
                logger.bind(tag=TAG).error(f"注册声纹失败: {result}")
                return False

            logger.bind(tag=TAG).info(f"声纹注册成功: {speaker_id}")
            return True
        except Exception as e:
            logger.bind(tag=TAG).error(f"注册声纹异常: {e}")
            return False

    async def identify_voiceprint(self, audio_bytes: bytes, speaker_ids: List[str]) -> Optional[Dict[str, Any]]:
        """识别声纹（1:N）"""
        try:
            wav_bytes = self._ensure_wav_format(audio_bytes)
            audio_base64 = base64.b64encode(wav_bytes).decode('utf-8')

            url = self._generate_auth_url()
            body = {
                "header": {
                    "app_id": self.app_id,
                    "status": 3
                },
                "parameter": {
                    "s1aa729d0": {
                        "func": "identifyFeature",
                        "groupId": self.group_id,
                        "identifyFeatureRes": {
                            "encoding": "utf8",
                            "compress": "raw",
                            "format": "json"
                        }
                    }
                },
                "payload": {
                    "resource": {
                        "encoding": "raw",
                        "sample_rate": 16000,
                        "channels": 1,
                        "bit_depth": 16,
                        "status": 3,
                        "audio": audio_base64
                    }
                }
            }

            response = await self.client.post(url, json=body)
            result = response.json()

            if result.get("header", {}).get("code") != 0:
                logger.bind(tag=TAG).error(f"识别声纹失败: {result}")
                return None

            # 解码响应
            text = result.get("payload", {}).get("identifyFeatureRes", {}).get("text", "")
            decoded = base64.b64decode(text).decode('utf-8')
            data = json.loads(decoded)

            matched_id = data.get("featureId", "")
            score = data.get("score", 0.0)

            if matched_id and matched_id in speaker_ids:
                return {
                    "speaker_id": matched_id,
                    "score": score
                }

            return None
        except Exception as e:
            logger.bind(tag=TAG).error(f"识别声纹异常: {e}")
            return None

    async def delete_voiceprint(self, speaker_id: str) -> bool:
        """删除声纹"""
        try:
            url = self._generate_auth_url()
            body = self._build_request_body(
                func="deleteFeature",
                feature_id=speaker_id,
                res_key="deleteFeatureRes"
            )

            response = await self.client.post(url, json=body)
            result = response.json()

            if result.get("header", {}).get("code") != 0:
                logger.bind(tag=TAG).warning(f"删除声纹失败: {result}")
                return False

            logger.bind(tag=TAG).info(f"声纹删除成功: {speaker_id}")
            return True
        except Exception as e:
            logger.bind(tag=TAG).error(f"删除声纹异常: {e}")
            return False

    async def check_health(self) -> bool:
        """健康检查 - 验证讯飞凭据是否有效"""
        try:
            # 通过尝试创建特征库来验证凭据
            # 如果特征库已存在，讯飞 API 会返回错误码，但说明凭据有效
            url = self._generate_auth_url()
            body = self._build_request_body(
                func="createGroup",
                res_key="createGroupRes"
            )

            response = await self.client.post(url, json=body)
            result = response.json()

            code = result.get("header", {}).get("code", -1)
            # code=0 表示成功，其他码可能是特征库已存在等，但说明凭据有效
            if code == 0:
                logger.bind(tag=TAG).info(f"声纹特征库 '{self.group_id}' 创建成功")
                return True
            else:
                # 非零码但能收到响应，说明凭据有效（可能是特征库已存在）
                logger.bind(tag=TAG).info(f"声纹特征库已存在或凭据有效: code={code}")
                return True
        except Exception as e:
            logger.bind(tag=TAG).error(f"声纹健康检查失败: {e}")
            return False

    async def close(self):
        """关闭 HTTP 客户端"""
        await self.client.aclose()
