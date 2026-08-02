"""小智听写助手 - 核心处理器

小智单方面播报单词，无语音交互判定：
1. 开场白 → (可选)单词介绍 → 逐个播报单词 → 结束语
2. 学生在纸上默写，不对语音答案做评判
3. 听写结束或中断时上报听写记录到 Java 后台
"""
import asyncio
import time
import uuid
from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional, TYPE_CHECKING

from config.logger import setup_logging
from config.manage_api_client import report_dictation_record
from core.providers.tts.dto.dto import TTSMessageDTO, SentenceType, ContentType
from core.utils.dialogue import Message
from core.utils.util import remove_punctuation_and_length

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

TAG = __name__
logger = setup_logging()


class DictationMode(Enum):
    """听写播报模式"""
    LISTEN_EN = "listen_en"  # 播报英文单词，学生写英文
    LISTEN_CN = "listen_cn"  # 播报中文释义，学生写英文


@dataclass
class DictationWord:
    """听写单词数据"""
    word: str = ""                            # 英文单词
    meaning: str = ""                         # 中文释义
    phonetic_us: str = ""                     # 美式音标
    phonetic_uk: str = ""                     # 英式音标
    example_sentence: str = ""                # 英文例句
    example_translation: str = ""             # 例句中文翻译
    synonyms: List[str] = field(default_factory=list)  # 近义词
    antonyms: List[str] = field(default_factory=list)  # 反义词

    @classmethod
    def from_dict(cls, item: dict) -> "DictationWord":
        return cls(
            word=item.get("word", "") or "",
            meaning=item.get("meaning", "") or "",
            phonetic_us=item.get("phoneticUs", "") or "",
            phonetic_uk=item.get("phoneticUk", "") or "",
            example_sentence=item.get("exampleSentence", "") or "",
            example_translation=item.get("exampleTranslation", "") or "",
            synonyms=list(item.get("synonyms") or []),
            antonyms=list(item.get("antonyms") or []),
        )

    def to_snapshot(self) -> dict:
        """导出为听写记录快照"""
        return {
            "word": self.word,
            "meaning": self.meaning,
            "phoneticUs": self.phonetic_us,
            "phoneticUk": self.phonetic_uk,
        }


@dataclass
class DictationSession:
    """听写会话状态机（单方面播报，无交互判定）"""
    task_id: str
    task_name: str
    mode: DictationMode
    accent: str                          # us / uk
    interval_seconds: float              # 单词间隔（供学生默写）
    repeat_count: int                    # 每词播报次数
    speak_rate: int                      # 语速（保留字段，未直接驱动 edge-tts）
    introduce_words: bool                # 听写前是否介绍所有单词
    show_example: bool                   # 介绍阶段是否播报例句
    example_translate: bool              # 介绍阶段是否翻译例句
    show_synonym: bool                   # 介绍阶段是否提示近义词/反义词
    words: List[DictationWord] = field(default_factory=list)

    current_word_index: int = 0
    is_active: bool = True
    is_speaking: bool = False            # 正在播报中（用于响应"停止听写"中断）
    start_time: float = 0.0
    stop_requested: bool = False

    def __post_init__(self):
        self.start_time = time.time()

    def get_current_word(self) -> Optional[DictationWord]:
        if self.current_word_index < len(self.words):
            return self.words[self.current_word_index]
        return None

    def advance_to_next_word(self) -> bool:
        """前进到下一个单词，返回 False 表示已播完"""
        self.current_word_index += 1
        return self.current_word_index < len(self.words)

    def to_words_snapshot(self) -> list:
        """导出本次播报的单词列表快照"""
        return [w.to_snapshot() for w in self.words]


# ============================================================================
# TTS 播报工具
# ============================================================================

# 听写会话使用单 FIRST/MIDDLE/LAST 会话模式：
# 只在开头发一次 FIRST（tts start），结尾发一次 LAST（tts stop），
# 中间所有文本都用 MIDDLE 发送，避免反复 start/stop 导致设备 ResetDecoder 丢失音频。


async def _begin_tts(conn: "ConnectionHandler"):
    """开启一个 TTS 会话（发送 FIRST），设备收到 tts state=start 后进入 Speaking 状态"""
    sentence_id = str(uuid.uuid4().hex)
    conn.sentence_id = sentence_id
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=sentence_id,
            sentence_type=SentenceType.FIRST,
            content_type=ContentType.ACTION,
        )
    )


async def _tts_text(conn: "ConnectionHandler", text: str):
    """在当前 TTS 会话中追加一段文本（MIDDLE），不会触发设备的 start/stop

    使用 WHOLE_TEXT 类型，跳过 TTS 内部的标点分段逻辑，
    整段文本直接生成一条语音，不受任何标点影响。
    """
    if not text:
        return
    sentence_id = conn.sentence_id or str(uuid.uuid4().hex)
    conn.sentence_id = sentence_id
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=sentence_id,
            sentence_type=SentenceType.MIDDLE,
            content_type=ContentType.WHOLE_TEXT,
            content_detail=text,
        )
    )
    conn.tts.store_tts_text(sentence_id, text)
    conn.dialogue.put(Message(role="assistant", content=text))


async def _end_tts(conn: "ConnectionHandler"):
    """结束当前 TTS 会话（发送 LAST），设备收到 tts state=stop 后退出 Speaking 状态"""
    sentence_id = conn.sentence_id or str(uuid.uuid4().hex)
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=sentence_id,
            sentence_type=SentenceType.LAST,
            content_type=ContentType.ACTION,
        )
    )


async def _speak_and_wait(conn: "ConnectionHandler", text: str, wait_after: float = 0.5):
    """在当前 TTS 会话中追加文本并等待音频播放完

    注意：调用前必须已调用 _begin_tts 开启会话。
    使用 WHOLE_TEXT 直接生成整段语音，无需 FLUSH。
    """
    if not text:
        return
    await _tts_text(conn, text)
    # 等待音频队列和 RateController 队列都发送完
    from core.handle.sendAudioHandle import _wait_for_audio_completion
    await _wait_for_audio_completion(conn)
    # 额外等待：确保客户端播放完音频
    estimated_duration = max(0.5, len(text) * 0.12)
    await asyncio.sleep(estimated_duration + wait_after)


# ============================================================================
# 听写主流程
# ============================================================================

async def start_dictation(conn: "ConnectionHandler", task_config: dict):
    """启动听写会话（由 dictation.py 插件调用）

    task_config 由 Java /dict/active 接口返回，包含：
      id, taskName, mode, accent, intervalSeconds, repeatCount, speakRate,
      introduceWords, showExample, exampleTranslate, showSynonym, words[]
    """
    words_raw = task_config.get("words") or []
    words = [DictationWord.from_dict(item) for item in words_raw]

    if not words:
        await _begin_tts(conn)
        await _speak_and_wait(conn, "没有找到听写单词，请先在后台配置听写任务哦。")
        await _end_tts(conn)
        return

    try:
        mode = DictationMode(task_config.get("mode", "listen_en"))
    except ValueError:
        mode = DictationMode.LISTEN_EN

    session = DictationSession(
        task_id=str(task_config.get("id", "")),
        task_name=str(task_config.get("taskName", "")),
        mode=mode,
        accent=task_config.get("accent", "us") or "us",
        interval_seconds=float(task_config.get("intervalSeconds", 5.0) or 5.0),
        repeat_count=int(task_config.get("repeatCount", 1) or 1),
        speak_rate=int(task_config.get("speakRate", 0) or 0),
        introduce_words=bool(task_config.get("introduceWords", False)),
        show_example=bool(task_config.get("showExample", False)),
        example_translate=bool(task_config.get("exampleTranslate", False)),
        show_synonym=bool(task_config.get("showSynonym", False)),
        words=words,
    )
    conn.dictation_session = session

    # 开启 TTS 会话（只发一次 FIRST/tts start，设备进入 Speaking 状态）
    await _begin_tts(conn)

    try:
        # 开场白
        opening = f"听写开始，共{len(words)}个单词。"
        await _speak_and_wait(conn, opening, wait_after=0.5)

        if not session.is_active:
            return

        # 单词介绍阶段
        if session.introduce_words:
            await _introduce_words(conn)

        if not session.is_active:
            return

        # 逐个播报单词
        await _speak_current_word(conn)
    finally:
        # 确保无论正常结束还是异常退出，都关闭 TTS 会话
        await _end_tts(conn)


async def _introduce_words(conn: "ConnectionHandler"):
    """听写前的单词介绍阶段：逐个介绍所有单词（中英文+例句+近反义词）"""
    session = conn.dictation_session
    if not session or not session.is_active:
        return

    for idx, word in enumerate(session.words, 1):
        if not session.is_active or session.stop_requested:
            return
        # 1. 中英文
        await _speak_and_wait(conn, f"{word.word}，{word.meaning}。", wait_after=1.0)
        # 2. 简单例句
        if session.show_example and word.example_sentence:
            await _speak_and_wait(conn, f"例句：{word.example_sentence}", wait_after=0.5)
            if session.example_translate and word.example_translation:
                await _speak_and_wait(conn, word.example_translation, wait_after=0.5)
        # 3. 近义词 / 反义词
        if session.show_synonym:
            tips = []
            if word.synonyms:
                tips.append(f"近义词：{'、'.join(word.synonyms[:3])}")
            if word.antonyms:
                tips.append(f"反义词：{'、'.join(word.antonyms[:3])}")
            if tips:
                await _speak_and_wait(conn, "。".join(tips) + "。", wait_after=1.0)


async def _speak_current_word(conn: "ConnectionHandler"):
    """播报当前单词（单方面播报，不等待学生回答）"""
    session = conn.dictation_session
    if not session or not session.is_active:
        return

    word = session.get_current_word()
    if not word:
        await _finish_dictation(conn)
        return

    session.is_speaking = True

    # 按模式播报
    for i in range(session.repeat_count):
        if not session.is_active or session.stop_requested:
            session.is_speaking = False
            return
        if session.mode == DictationMode.LISTEN_EN:
            text = word.word
        else:
            text = word.meaning or word.word
        # 重复时增加提示
        if session.repeat_count > 1:
            text = f"{text}。"
        await _speak_and_wait(conn, text, wait_after=0.3)

    session.is_speaking = False

    if not session.is_active or session.stop_requested:
        return

    # 间隔等待（供学生默写）
    try:
        await asyncio.sleep(session.interval_seconds)
    except asyncio.CancelledError:
        return

    if not session.is_active or session.stop_requested:
        return

    # 前进到下一个单词
    session.advance_to_next_word()
    await _speak_current_word(conn)


async def _finish_dictation(conn: "ConnectionHandler"):
    """正常播完所有单词后的收尾"""
    session = conn.dictation_session
    if not session:
        return

    await _speak_and_wait(conn, "听写结束。", wait_after=0.5)
    # TTS 会话由 start_dictation 的 finally 关闭，这里不再调用 _end_tts
    await _report_and_clear(conn, end_time=time.time())


async def stop_dictation(conn: "ConnectionHandler", speak_hint: bool = True):
    """用户主动中断听写（"停止听写" / "退出听写"）"""
    session = conn.dictation_session
    if not session:
        return

    session.stop_requested = True
    session.is_active = False

    if speak_hint:
        await _speak_and_wait(conn, "好的，听写已停止。", wait_after=0.5)
    # TTS 会话由 start_dictation 的 finally 关闭，这里不再调用 _end_tts
    await _report_and_clear(conn, end_time=time.time())


async def _report_and_clear(conn: "ConnectionHandler", end_time: float):
    """上报听写记录并清除会话状态"""
    session = conn.dictation_session
    if not session:
        return

    duration_seconds = int(end_time - session.start_time) if session.start_time else 0
    start_ms = int(session.start_time * 1000) if session.start_time else None
    end_ms = int(end_time * 1000)

    # 清除听写会话引用
    session.is_active = False
    conn.dictation_session = None

    # 异步上报，不阻塞主流程
    try:
        await report_dictation_record(
            mac_address=conn.device_id or "",
            device_id=conn.headers.get("device-id", "") if hasattr(conn, "headers") else "",
            task_id=session.task_id,
            task_name=session.task_name,
            total_words=len(session.words),
            words=session.to_words_snapshot(),
            start_time=start_ms,
            end_time=end_ms,
            duration_seconds=duration_seconds,
        )
    except Exception as e:
        logger.bind(tag=TAG).error(f"上报听写记录失败: {e}")


# ============================================================================
# 听写过程中的用户语音中断处理
# ============================================================================

# 听写过程中识别的中断命令（除常规 cmd_exit 外）
DICTATION_STOP_KEYWORDS = [
    "停止听写", "退出听写", "结束听写", "停止报听写", "不要听写了",
    "停", "结束", "停止",
]


async def handle_dictation_interrupt(conn: "ConnectionHandler", text: str) -> bool:
    """听写进行中收到用户语音时的处理（仅响应停止/退出类命令）

    返回 True 表示已被听写流程处理（不应继续走常规聊天）；
    返回 False 表示用户说的话不是中断命令，应当被忽略（单方面播报模式下不做评判）。
    """
    session = conn.dictation_session
    if not session or not session.is_active:
        return False

    _, filtered = remove_punctuation_and_length(text)
    filtered = (filtered or "").strip()

    # 命中停止类关键词
    for kw in DICTATION_STOP_KEYWORDS:
        if kw in filtered:
            conn.logger.bind(tag=TAG).info(f"识别到听写中断命令: {filtered}")
            await stop_dictation(conn, speak_hint=True)
            return True

    # 单方面播报模式：用户说的其他话不响应（不评判答案）
    # 给一个简短提示，避免用户以为设备没听到
    await _speak_and_wait(conn, '我在听写中，请等我报完。如需停止，请说"停止听写"。', wait_after=0.3)
    return True
