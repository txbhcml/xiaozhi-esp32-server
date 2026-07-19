import asyncio
import time
import aiohttp
import requests
from urllib.parse import urlparse, parse_qs
from typing import Optional, Dict
from config.logger import setup_logging
from core.utils.cache.manager import cache_manager
from core.utils.cache.config import CacheType

TAG = __name__
logger = setup_logging()


class VoiceprintProvider:
    """声纹识别服务提供者

    支持两种配置格式：
    1. 新格式（直接调用声纹 API）：
       {"type": "xunfei", "app_id": "...", "api_key": "...", "speakers": [...], ...}
    2. 旧格式（通过 HTTP 调用外部声纹服务，向后兼容）：
       {"url": "http://host:port/voiceprint/health?key=token", "speakers": [...], ...}
    """

    def __init__(self, config: dict):
        self.speakers = config.get("speakers", [])
        self.speaker_map = self._parse_speakers()
        self.similarity_threshold = float(config.get("similarity_threshold", 0.4))

        # 声纹 provider 实例（新格式）
        self.provider = None
        self.provider_type = None

        # 旧格式字段（HTTP 调用外部服务）
        self.original_url = config.get("url", "")
        self.api_url = None
        self.api_key = None

        # 共用字段
        self.speaker_ids = []
        self.enabled = False

        # 提取 speaker_ids（两种格式共用）
        for speaker_str in self.speakers:
            try:
                parts = speaker_str.split(",", 2)
                if len(parts) >= 1:
                    self.speaker_ids.append(parts[0].strip())
            except Exception:
                continue

        # 检查是否有有效的说话人配置
        if not self.speaker_ids:
            logger.bind(tag=TAG).warning("未配置有效的说话人，声纹识别将被禁用")
            return

        # 根据配置格式选择初始化方式
        provider_type = config.get("type")
        if provider_type:
            # 新格式：创建声纹 provider 实例，直接调用 API
            self._init_provider(provider_type, config)
        elif self.original_url:
            # 旧格式：通过 HTTP 调用外部声纹服务（向后兼容）
            self._init_http(config)
        else:
            logger.bind(tag=TAG).warning("声纹识别配置不完整（需要 type 或 url），声纹识别将被禁用")

    def _init_provider(self, provider_type: str, config: dict):
        """新格式：创建声纹 provider 实例，直接调用声纹 API"""
        try:
            from core.providers.voiceprint import create_instance
            self.provider = create_instance(provider_type, config)
            self.provider_type = provider_type
            self.enabled = True
            logger.bind(tag=TAG).info(
                f"声纹识别已启用: type={provider_type}, "
                f"说话人={len(self.speaker_ids)}个, "
                f"相似度阈值={self.similarity_threshold}"
            )
        except Exception as e:
            logger.bind(tag=TAG).error(f"创建声纹 provider 失败: {e}")
            self.enabled = False

    def _init_http(self, config: dict):
        """旧格式：通过 HTTP 调用外部声纹服务（向后兼容）"""
        # 解析URL和key
        parsed_url = urlparse(self.original_url)
        base_url = f"{parsed_url.scheme}://{parsed_url.netloc}"

        # 从查询参数中提取key
        query_params = parse_qs(parsed_url.query)
        self.api_key = query_params.get('key', [''])[0]

        if not self.api_key:
            logger.bind(tag=TAG).error("URL中未找到key参数，声纹识别将被禁用")
            return

        # 构造identify接口地址
        self.api_url = f"{base_url}/voiceprint/identify"

        # 进行健康检查，验证服务器是否可用
        if self._check_server_health():
            self.enabled = True
            logger.bind(tag=TAG).info(
                f"声纹识别已启用: API={self.api_url}, "
                f"说话人={len(self.speaker_ids)}个, "
                f"相似度阈值={self.similarity_threshold}"
            )
        else:
            self.enabled = False
            logger.bind(tag=TAG).warning(f"声纹识别服务器不可用，声纹识别已禁用: {self.api_url}")

    def _parse_speakers(self) -> Dict[str, Dict[str, str]]:
        """解析说话人配置"""
        speaker_map = {}
        for speaker_str in self.speakers:
            try:
                parts = speaker_str.split(",", 2)
                if len(parts) >= 3:
                    speaker_id, name, description = parts[0].strip(), parts[1].strip(), parts[2].strip()
                    speaker_map[speaker_id] = {
                        "name": name,
                        "description": description
                    }
            except Exception as e:
                logger.bind(tag=TAG).warning(f"解析说话人配置失败: {speaker_str}, 错误: {e}")
        return speaker_map

    def _check_server_health(self) -> bool:
        """检查声纹识别服务器健康状态（旧格式 HTTP 模式）"""
        if not self.api_url or not self.api_key:
            return False

        cache_key = f"{self.api_url}:{self.api_key}"

        # 检查缓存
        cached_result = cache_manager.get(CacheType.VOICEPRINT_HEALTH, cache_key)
        if cached_result is not None:
            logger.bind(tag=TAG).debug(f"使用缓存的健康状态: {cached_result}")
            return cached_result

        # 缓存过期或不存在
        logger.bind(tag=TAG).info("执行声纹服务器健康检查")

        try:
            # 健康检查URL
            parsed_url = urlparse(self.api_url)
            health_url = f"{parsed_url.scheme}://{parsed_url.netloc}/voiceprint/health?key={self.api_key}"

            # 发送健康检查请求
            response = requests.get(health_url, timeout=3)

            if response.status_code == 200:
                result = response.json()
                if result.get("status") == "healthy":
                    logger.bind(tag=TAG).info("声纹识别服务器健康检查通过")
                    is_healthy = True
                else:
                    logger.bind(tag=TAG).warning(f"声纹识别服务器状态异常: {result}")
                    is_healthy = False
            else:
                logger.bind(tag=TAG).warning(f"声纹识别服务器健康检查失败: HTTP {response.status_code}")
                is_healthy = False

        except requests.exceptions.ConnectTimeout:
            logger.bind(tag=TAG).warning("声纹识别服务器连接超时")
            is_healthy = False
        except requests.exceptions.ConnectionError:
            logger.bind(tag=TAG).warning("声纹识别服务器连接被拒绝")
            is_healthy = False
        except Exception as e:
            logger.bind(tag=TAG).warning(f"声纹识别服务器健康检查异常: {e}")
            is_healthy = False

        # 使用全局缓存管理器缓存结果
        cache_manager.set(CacheType.VOICEPRINT_HEALTH, cache_key, is_healthy)
        logger.bind(tag=TAG).info(f"健康检查结果已缓存: {is_healthy}")

        return is_healthy

    async def identify_speaker(self, audio_data: bytes, session_id: str) -> Optional[str]:
        """识别说话人"""
        if not self.enabled:
            logger.bind(tag=TAG).debug("声纹识别功能已禁用或未配置，跳过识别")
            return None

        if self.provider:
            # 新格式：直接调用声纹 provider
            return await self._identify_via_provider(audio_data)
        else:
            # 旧格式：通过 HTTP 调用外部声纹服务
            return await self._identify_via_http(audio_data)

    async def _identify_via_provider(self, audio_data: bytes) -> Optional[str]:
        """新格式：通过声纹 provider 直接调用 API 识别"""
        try:
            api_start_time = time.monotonic()

            result = await self.provider.identify_voiceprint(audio_data, self.speaker_ids)

            total_elapsed_time = time.monotonic() - api_start_time
            logger.bind(tag=TAG).info(f"声纹识别耗时: {total_elapsed_time:.3f}s")

            if result is None:
                logger.bind(tag=TAG).info("声纹未匹配到已注册的说话人")
                return "未知说话人"

            speaker_id = result.get("speaker_id")
            score = result.get("score", 0)

            # 相似度阈值检查
            if score < self.similarity_threshold:
                logger.bind(tag=TAG).warning(
                    f"声纹识别相似度{score:.3f}低于阈值{self.similarity_threshold}"
                )
                return "未知说话人"

            if speaker_id and speaker_id in self.speaker_map:
                result_name = self.speaker_map[speaker_id]["name"]
                logger.bind(tag=TAG).info(f"声纹识别成功: {result_name} (相似度: {score:.3f})")
                return result_name
            else:
                logger.bind(tag=TAG).warning(f"未识别的说话人ID: {speaker_id}")
                return "未知说话人"

        except Exception as e:
            elapsed = time.monotonic() - api_start_time
            logger.bind(tag=TAG).error(f"声纹识别失败: {e}, 耗时: {elapsed:.3f}s")
            return None

    async def _identify_via_http(self, audio_data: bytes) -> Optional[str]:
        """旧格式：通过 HTTP 调用外部声纹服务识别（向后兼容）"""
        if not self.api_url or not self.api_key:
            return None

        try:
            api_start_time = time.monotonic()

            # 准备请求头
            headers = {
                'Authorization': f'Bearer {self.api_key}',
                'Accept': 'application/json'
            }

            # 准备multipart/form-data数据
            data = aiohttp.FormData()
            data.add_field('speaker_ids', ','.join(self.speaker_ids))
            data.add_field('file', audio_data, filename='audio.wav', content_type='audio/wav')

            timeout = aiohttp.ClientTimeout(total=10)

            # 网络请求
            async with aiohttp.ClientSession(timeout=timeout) as session:
                async with session.post(self.api_url, headers=headers, data=data) as response:

                    if response.status == 200:
                        result = await response.json()
                        speaker_id = result.get("speaker_id")
                        score = result.get("score", 0)
                        total_elapsed_time = time.monotonic() - api_start_time

                        logger.bind(tag=TAG).info(f"声纹识别耗时: {total_elapsed_time:.3f}s")

                        # 相似度阈值检查
                        if score < self.similarity_threshold:
                            logger.bind(tag=TAG).warning(
                                f"声纹识别相似度{score:.3f}低于阈值{self.similarity_threshold}"
                            )
                            return "未知说话人"

                        if speaker_id and speaker_id in self.speaker_map:
                            result_name = self.speaker_map[speaker_id]["name"]
                            logger.bind(tag=TAG).info(
                                f"声纹识别成功: {result_name} (相似度: {score:.3f})"
                            )
                            return result_name
                        else:
                            logger.bind(tag=TAG).warning(f"未识别的说话人ID: {speaker_id}")
                            return "未知说话人"
                    else:
                        logger.bind(tag=TAG).error(f"声纹识别API错误: HTTP {response.status}")
                        return None

        except asyncio.TimeoutError:
            elapsed = time.monotonic() - api_start_time
            logger.bind(tag=TAG).error(f"声纹识别超时: {elapsed:.3f}s")
            return None
        except Exception as e:
            elapsed = time.monotonic() - api_start_time
            logger.bind(tag=TAG).error(f"声纹识别失败: {e}, 耗时: {elapsed:.3f}s")
            return None

    async def close(self):
        """关闭资源"""
        if self.provider:
            await self.provider.close()
