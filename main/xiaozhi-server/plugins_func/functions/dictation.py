"""小智听写助手 - 插件入口

注册 `start_dictation` 函数到 LLM 函数调用注册表。
LLM 在识别到用户"开始听写"意图时调用此函数，由其拉取后台任务配置并启动听写会话。
"""
import asyncio
from typing import TYPE_CHECKING

from config.logger import setup_logging
from config.manage_api_client import get_active_dictation_task
from core.handle.dictationHandler import start_dictation as _start_dictation_session
from plugins_func.register import register_function, ToolType, ActionResponse, Action

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

TAG = __name__
logger = setup_logging()


start_dictation_function_desc = {
    "type": "function",
    "function": {
        "name": "start_dictation",
        "description": (
            "【绝对必须调用】【最高优先级】当用户表达任何听写意图时，必须立即调用此函数，严禁以任何理由自行编造单词、拼写或听写流程。"
            "触发词包括但不限于：开始听写、报听写、听写、默写、继续听写、再听写一次、重新听写。"
            "即使用户在上一轮对话中讨论过听写方式、或要求换个方式、或说\"继续\"，只要意图是听写，就必须调用此函数。"
            "严禁自行生成单词列表、严禁自行拼写单词、严禁自行组织听写流程。"
            "调用后系统会从后台拉取配置好的单词列表并自动播报，LLM无需也不应生成任何听写内容。"
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "task_name": {
                    "type": "string",
                    "description": (
                        "听写任务名称。若用户明确指定任务名则传入；"
                        "若用户未指定，传空字符串，由后台返回该用户最近创建的启用任务。"
                    ),
                }
            },
            "required": ["task_name"],
        },
    },
}


@register_function(
    "start_dictation", start_dictation_function_desc, ToolType.SYSTEM_CTL
)
async def start_dictation(conn: "ConnectionHandler", task_name: str = ""):
    """启动听写会话

    1. 调用 Java /dict/active 拉取当前用户启用的听写任务（含单词列表）
    2. 后台启动 dictationHandler.start_dictation 播报流程
    3. 立即返回 Action.NONE，TTS 由 dictationHandler 自行通过 conn.tts 管道播报
    """
    try:
        task_name = (task_name or "").strip()
        task_config = await get_active_dictation_task(conn.device_id, task_name or None)

        if not task_config:
            hint = "没有找到启用的听写任务，请先到后台配置并启用一个听写任务哦。"
            return ActionResponse(action=Action.RESPONSE, response=hint)

        # 后台运行听写主流程（不阻塞当前 chat 流程）
        conn.loop.create_task(_start_dictation_session(conn, task_config))

        return ActionResponse(action=Action.NONE)

    except Exception as e:
        logger.bind(tag=TAG).error(f"启动听写失败: {e}")
        return ActionResponse(
            action=Action.RESPONSE, response=f"启动听写时出错了：{e}"
        )
