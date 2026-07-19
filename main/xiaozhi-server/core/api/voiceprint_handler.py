"""声纹服务 HTTP 接口处理器

提供声纹注册、删除、识别、健康检查接口，供 manager-api 通过 HTTP 调用。
manager-api 通过 X-Voiceprint-Config 请求头传递声纹 provider 凭据
（type/app_id/api_key/api_secret/group_id），处理器据此创建一次性 provider
实例并调用对应声纹 API。
"""

import json
from aiohttp import web
from config.logger import setup_logging
from core.api.base_handler import BaseHandler
from core.providers.voiceprint import create_instance

TAG = __name__

# 设置最大音频文件大小为 10MB
MAX_AUDIO_SIZE = 10 * 1024 * 1024


class VoiceprintHandler(BaseHandler):
    def __init__(self, config: dict):
        super().__init__(config)

    def _verify_auth(self, request) -> bool:
        """验证请求的 Authorization 头

        auth_key 未配置时跳过验证（便于本地开发）；
        配置后要求 Bearer token 与 auth_key 一致。
        """
        auth_key = self.config.get("server", {}).get("auth_key", "")
        if not auth_key:
            return True
        auth_header = request.headers.get("Authorization", "")
        if not auth_header.startswith("Bearer "):
            return False
        return auth_header[7:] == auth_key

    def _get_provider_config(self, request) -> dict:
        """从 X-Voiceprint-Config 请求头读取声纹 provider 凭据"""
        config_header = request.headers.get("X-Voiceprint-Config", "")
        if not config_header:
            raise ValueError("缺少 X-Voiceprint-Config 请求头，无法创建声纹 provider")
        return json.loads(config_header)

    async def _create_provider(self, request):
        """根据请求头凭据创建声纹 provider 实例"""
        config = self._get_provider_config(request)
        provider_type = config.get("type")
        if not provider_type:
            raise ValueError("声纹配置缺少 type 字段")
        return create_instance(provider_type, config)

    async def _read_multipart_fields(self, request, id_field_name: str):
        """读取 multipart/form-data 中的 id 字段和音频文件

        Args:
            request: aiohttp 请求对象
            id_field_name: id 字段名（speaker_id 或 speaker_ids）

        Returns:
            (id_value, audio_data) 元组
        """
        reader = await request.multipart()
        id_value = None
        audio_data = None

        field = await reader.next()
        while field is not None:
            if field.name == id_field_name:
                id_value = (await field.text()).strip()
            elif field.name == "file":
                audio_data = await field.read()
                if audio_data and len(audio_data) > MAX_AUDIO_SIZE:
                    raise ValueError(
                        f"音频文件大小超过限制，最大允许{MAX_AUDIO_SIZE / 1024 / 1024}MB"
                    )
            field = await reader.next()

        if not id_value:
            raise ValueError(f"缺少 {id_field_name} 字段")
        if not audio_data:
            raise ValueError("缺少音频文件")

        return id_value, audio_data

    async def handle_register(self, request):
        """POST /voiceprint/register - 注册声纹

        请求体（multipart/form-data）:
            - speaker_id: 说话人ID
            - file: 音频文件（WAV/PCM）
        """
        response = None
        provider = None
        try:
            if not self._verify_auth(request):
                return self._json_response({"success": False, "message": "认证失败"}, status=401)

            provider = await self._create_provider(request)
            speaker_id, audio_data = await self._read_multipart_fields(request, "speaker_id")

            success = await provider.register_voiceprint(speaker_id, audio_data)
            self.logger.bind(tag=TAG).info(f"声纹注册: speaker_id={speaker_id}, success={success}")
            response = self._json_response({"success": success})
        except ValueError as e:
            self.logger.bind(tag=TAG).error(f"声纹注册请求异常: {e}")
            response = self._json_response({"success": False, "message": str(e)}, status=400)
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"声纹注册请求异常: {e}")
            response = self._json_response({"success": False, "message": "处理请求时发生错误"}, status=500)
        finally:
            if provider:
                await provider.close()
            if response:
                self._add_cors_headers(response)
            return response

    async def handle_delete(self, request):
        """DELETE /voiceprint/{id} - 删除声纹

        路径参数:
            - id: 声纹ID（speaker_id）
        """
        response = None
        provider = None
        try:
            if not self._verify_auth(request):
                return self._json_response({"success": False, "message": "认证失败"}, status=401)

            provider = await self._create_provider(request)
            voice_print_id = request.match_info.get("id", "")
            if not voice_print_id:
                raise ValueError("缺少声纹ID")

            success = await provider.delete_voiceprint(voice_print_id)
            self.logger.bind(tag=TAG).info(f"声纹删除: speaker_id={voice_print_id}, success={success}")
            response = self._json_response({"success": success})
        except ValueError as e:
            self.logger.bind(tag=TAG).error(f"声纹删除请求异常: {e}")
            response = self._json_response({"success": False, "message": str(e)}, status=400)
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"声纹删除请求异常: {e}")
            response = self._json_response({"success": False, "message": "处理请求时发生错误"}, status=500)
        finally:
            if provider:
                await provider.close()
            if response:
                self._add_cors_headers(response)
            return response

    async def handle_identify(self, request):
        """POST /voiceprint/identify - 识别声纹（1:N）

        请求体（multipart/form-data）:
            - speaker_ids: 逗号分隔的说话人ID列表
            - file: 音频文件（WAV/PCM）

        返回:
            - speaker_id: 匹配的说话人ID（未匹配为空字符串）
            - score: 相似度分数
        """
        response = None
        provider = None
        try:
            if not self._verify_auth(request):
                return self._json_response(
                    {"speaker_id": "", "score": 0, "message": "认证失败"}, status=401
                )

            provider = await self._create_provider(request)
            speaker_ids_str, audio_data = await self._read_multipart_fields(request, "speaker_ids")

            speaker_ids = [s.strip() for s in speaker_ids_str.split(",") if s.strip()]
            result_data = await provider.identify_voiceprint(audio_data, speaker_ids)

            if result_data:
                result = {
                    "speaker_id": result_data.get("speaker_id", ""),
                    "score": result_data.get("score", 0),
                }
                self.logger.bind(tag=TAG).info(
                    f"声纹识别: matched={result['speaker_id']}, score={result['score']}"
                )
            else:
                result = {"speaker_id": "", "score": 0}
                self.logger.bind(tag=TAG).info("声纹识别: 未匹配到说话人")
            response = self._json_response(result)
        except ValueError as e:
            self.logger.bind(tag=TAG).error(f"声纹识别请求异常: {e}")
            response = self._json_response({"speaker_id": "", "score": 0, "message": str(e)}, status=400)
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"声纹识别请求异常: {e}")
            response = self._json_response(
                {"speaker_id": "", "score": 0, "message": "处理请求时发生错误"}, status=500
            )
        finally:
            if provider:
                await provider.close()
            if response:
                self._add_cors_headers(response)
            return response

    async def handle_health(self, request):
        """GET /voiceprint/health - 健康检查"""
        try:
            response = self._json_response({"status": "healthy"})
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"声纹健康检查异常: {e}")
            response = self._json_response(
                {"status": "unhealthy", "message": str(e)}, status=500
            )
        finally:
            self._add_cors_headers(response)
            return response

    async def handle_options(self, request):
        """处理 OPTIONS 请求，添加 CORS 头信息"""
        response = web.Response(body=b"", content_type="text/plain")
        self._add_cors_headers(response)
        response.headers["Access-Control-Allow-Methods"] = "GET, POST, DELETE, OPTIONS"
        return response

    def _json_response(self, data: dict, status: int = 200) -> web.Response:
        """创建统一的 JSON 响应"""
        return web.Response(
            text=json.dumps(data, separators=(",", ":")),
            content_type="application/json",
            status=status,
        )
