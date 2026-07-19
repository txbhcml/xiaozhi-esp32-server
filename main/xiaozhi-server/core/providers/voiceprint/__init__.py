"""声纹识别提供者工厂模块"""

import importlib
import os
import sys
from core.providers.voiceprint.base import VoiceprintProviderBase
from config.logger import setup_logging

TAG = __name__
logger = setup_logging()


def create_instance(class_name: str, config: dict) -> VoiceprintProviderBase:
    """工厂方法创建声纹识别提供者实例

    Args:
        class_name: 提供者类名（如 xunfei）
        config: 配置字典

    Returns:
        VoiceprintProviderBase: 声纹识别提供者实例
    """
    # 检查 provider 文件是否存在
    provider_path = os.path.join('core', 'providers', 'voiceprint', f'{class_name}.py')
    if os.path.exists(provider_path):
        lib_name = f'core.providers.voiceprint.{class_name}'
        if lib_name not in sys.modules:
            sys.modules[lib_name] = importlib.import_module(lib_name)
        return sys.modules[lib_name].VoiceprintProvider(config)

    raise ValueError(f"不支持的声纹识别类型: {class_name}，请检查该配置的type是否设置正确")
