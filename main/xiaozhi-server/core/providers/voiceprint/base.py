"""声纹识别提供者基类"""

from abc import ABC, abstractmethod
from typing import Optional, Dict, Any, List


class VoiceprintProviderBase(ABC):
    """声纹识别提供者抽象基类"""

    def __init__(self, config: dict):
        """初始化声纹识别提供者

        Args:
            config: 配置字典，包含 provider 相关配置
        """
        self.config = config

    @abstractmethod
    async def register_voiceprint(self, speaker_id: str, audio_bytes: bytes, feature_info: str = "") -> bool:
        """注册声纹

        Args:
            speaker_id: 说话人ID
            audio_bytes: 音频数据（WAV或PCM格式）
            feature_info: 特征信息（可选）

        Returns:
            bool: 是否注册成功
        """
        pass

    @abstractmethod
    async def identify_voiceprint(self, audio_bytes: bytes, speaker_ids: List[str]) -> Optional[Dict[str, Any]]:
        """识别声纹（1:N）

        Args:
            audio_bytes: 音频数据（WAV或PCM格式）
            speaker_ids: 待匹配的说话人ID列表

        Returns:
            Optional[Dict]: 匹配结果，包含 speaker_id 和 score；未匹配返回 None
        """
        pass

    @abstractmethod
    async def delete_voiceprint(self, speaker_id: str) -> bool:
        """删除声纹

        Args:
            speaker_id: 说话人ID

        Returns:
            bool: 是否删除成功
        """
        pass

    @abstractmethod
    async def check_health(self) -> bool:
        """健康检查

        Returns:
            bool: 服务是否可用
        """
        pass

    async def close(self):
        """关闭资源（如HTTP客户端）"""
        pass
