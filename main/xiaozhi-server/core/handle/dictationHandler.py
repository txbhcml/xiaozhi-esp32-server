"""小智听写助手 - 核心处理器

小智单方面播报单词，无语音交互判定：
1. 开场白 → 逐个播报单词 → 结束语
2. 学生在纸上默写，不对语音答案做评判
3. 英文单词优先使用有道词典发音，失败时回退到 EdgeTTS
4. 语速仅对单词播报生效，开场白/结束语使用正常语速
5. 听写结束或中断时上报听写记录到 Java 后台
"""
import asyncio
import os
import re
import time
import uuid
from dataclasses import dataclass, field
from enum import Enum
from typing import List, Optional, TYPE_CHECKING

import aiohttp

from config.logger import setup_logging
from config.manage_api_client import report_dictation_record
from core.providers.tts.dto.dto import TTSMessageDTO, SentenceType, ContentType
from core.utils.dialogue import Message
from core.utils.util import remove_punctuation_and_length

if TYPE_CHECKING:
    from core.connection import ConnectionHandler

TAG = __name__
logger = setup_logging()

# 听写专用英文音色（按口音选择）
DICTATION_EN_VOICES = {
    "us": "en-US-JennyNeural",   # 美式女声
    "uk": "en-GB-SoniaNeural",   # 英式女声
}

# 检测文本是否主要为英文
_EN_TEXT_PATTERN = re.compile(r'[a-zA-Z]')


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
    speak_rate: int                      # 语速（仅对单词播报生效）
    repeat_interval: float               # 重复播报同一单词的间隔（秒）
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


def _apply_dictation_rate(conn: "ConnectionHandler", rate_offset: int):
    """临时应用听写语速到 TTS 引擎

    Args:
        rate_offset: 语速偏移量，-100~100，映射为 EdgeTTS rate 格式如 +50%/-20%
                     0 表示使用默认语速（不修改）
    """
    if rate_offset == 0:
        return
    if hasattr(conn.tts, 'speech_rate'):
        conn.tts.speech_rate = rate_offset
    if hasattr(conn.tts, 'edge_rate'):
        conn.tts.edge_rate = f"{rate_offset:+}%"


def _restore_original_rate(conn: "ConnectionHandler", original_rate: int):
    """恢复 TTS 原始语速"""
    if hasattr(conn.tts, 'speech_rate'):
        conn.tts.speech_rate = original_rate
    if hasattr(conn.tts, 'edge_rate'):
        conn.tts.edge_rate = f"{original_rate:+}%"


async def _speak_and_wait(conn: "ConnectionHandler", text: str, wait_after: float = 0.5,
                          voice: str = None, rate: int = None):
    """在当前 TTS 会话中追加文本并等待音频播放完

    注意：调用前必须已调用 _begin_tts 开启会话。
    使用 WHOLE_TEXT 直接生成整段语音，无需 FLUSH。
    等待策略：RateController 队列清空（发送完）+ 估算播放时间（设备播完）+ 缓冲间隔

    Args:
        voice: 指定音色（如 en-US-JennyNeural），为 None 时使用当前音色。
               通过临时替换 conn.tts.voice 实现，播完后恢复。
        rate: 指定语速偏移（-100~100），为 None 时使用当前语速。
              通过临时替换 conn.tts.speech_rate/edge_rate 实现，播完后恢复。
    """
    if not text:
        return

    # 如果指定了音色，临时替换
    original_voice = None
    if voice and hasattr(conn.tts, 'voice') and conn.tts.voice != voice:
        original_voice = conn.tts.voice
        conn.tts.voice = voice

    # 如果指定了语速，临时替换（无条件设置 edge_rate，防止被其他流程重置后失效）
    original_rate = None
    if rate is not None and hasattr(conn.tts, 'speech_rate'):
        original_rate = conn.tts.speech_rate
        _apply_dictation_rate(conn, rate)

    try:
        await _tts_text(conn, text)
        # 1. 等待 RateController 队列清空（音频全部发送到网络）
        from core.handle.sendAudioHandle import _wait_for_audio_completion
        await _wait_for_audio_completion(conn)
        # 2. 估算设备端播放时间并等待，确保设备把音频播完再发下一段
        #    中文约 4-5 字/秒，英文约 3 词/秒，取较慢的 0.15 秒/字
        estimated_playback = max(1.0, len(text) * 0.15)
        await asyncio.sleep(estimated_playback + wait_after)
    finally:
        # 恢复原始音色
        if original_voice:
            conn.tts.voice = original_voice
        # 恢复原始语速
        if original_rate is not None:
            _restore_original_rate(conn, original_rate)


# ============================================================================
# 听写主流程
# ============================================================================

async def start_dictation(conn: "ConnectionHandler", task_config: dict):
    """启动听写会话（由 dictation.py 插件调用）

    task_config 由 Java /dict/active 接口返回，包含：
      id, taskName, mode, accent, intervalSeconds, repeatCount, speakRate,
      repeatIntervalSeconds, words[]
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

    speak_rate = int(task_config.get("speakRate", 0) or 0)

    session = DictationSession(
        task_id=str(task_config.get("id", "")),
        task_name=str(task_config.get("taskName", "")),
        mode=mode,
        accent=task_config.get("accent", "us") or "us",
        interval_seconds=float(task_config.get("intervalSeconds", 5.0) or 5.0),
        repeat_count=int(task_config.get("repeatCount", 1) or 1),
        speak_rate=speak_rate,
        repeat_interval=float(task_config.get("repeatIntervalSeconds", 1.0) or 1.0),
        words=words,
    )
    conn.dictation_session = session

    # 开启 TTS 会话（只发一次 FIRST/tts start，设备进入 Speaking 状态）
    await _begin_tts(conn)

    try:
        # 开场白（正常语速，不应用 speak_rate）
        opening = f"听写开始，共{len(words)}个单词。"
        await _speak_and_wait(conn, opening, wait_after=0.5)

        if not session.is_active:
            return

        # 逐个播报单词（语速仅对单词生效）
        await _speak_current_word(conn)
    finally:
        # 确保无论正常结束还是异常退出，都关闭 TTS 会话
        await _end_tts(conn)


async def _download_youdao_audio(word: str, accent: str) -> Optional[bytes]:
    """从有道词典 API 下载单词发音 MP3

    Args:
        word: 英文单词
        accent: 'us' (type=0) 或 'uk' (type=1)
    Returns:
        MP3 二进制数据，失败返回 None
    """
    youdao_type = 0 if accent == "us" else 1
    url = f"http://dict.youdao.com/dictvoice?audio={word}&type={youdao_type}"
    try:
        async with aiohttp.ClientSession() as http_session:
            async with http_session.get(url, timeout=aiohttp.ClientTimeout(total=5)) as resp:
                if resp.status == 200:
                    data = await resp.read()
                    if len(data) > 100:
                        return data
        return None
    except Exception as e:
        logger.bind(tag=TAG).warning(f"有道词典下载失败: {word}, {e}")
        return None


async def _speak_youdao_word(conn: "ConnectionHandler", text: str, mp3_data: bytes,
                             wait_after: float = 0.5):
    """通过有道词典 MP3 播报单词发音

    将 MP3 数据写入临时文件，通过 TTS 管线的 FILE 内容类型播放。
    """
    tmp_dir = conn.tts.output_file
    os.makedirs(tmp_dir, exist_ok=True)
    tmp_file = os.path.join(tmp_dir, f"youdao-{uuid.uuid4().hex}.mp3")
    with open(tmp_file, "wb") as f:
        f.write(mp3_data)

    sentence_id = conn.sentence_id or str(uuid.uuid4().hex)
    conn.sentence_id = sentence_id

    # 先放入文本条目（供设备屏幕显示）
    conn.tts.tts_audio_queue.put((SentenceType.MIDDLE, None, text, sentence_id))
    conn.tts.store_tts_text(sentence_id, text)
    conn.dialogue.put(Message(role="assistant", content=text))

    # 通过 FILE 内容类型让 TTS 线程将 MP3 转为 opus 并发送
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=sentence_id,
            sentence_type=SentenceType.MIDDLE,
            content_type=ContentType.FILE,
            content_file=tmp_file,
        )
    )

    # 等待音频发送和播放完成
    from core.handle.sendAudioHandle import _wait_for_audio_completion
    await _wait_for_audio_completion(conn)
    estimated_playback = max(1.0, len(text) * 0.3)
    await asyncio.sleep(estimated_playback + wait_after)


async def _speak_current_word(conn: "ConnectionHandler"):
    """播报当前单词（单方面播报，不等待学生回答）

    语速仅对单词播报生效（通过 _speak_and_wait 的 rate 参数传入）。
    英文单词模式优先使用有道词典发音，失败时回退到 EdgeTTS。
    """
    session = conn.dictation_session
    if not session or not session.is_active:
        return

    word = session.get_current_word()
    if not word:
        await _finish_dictation(conn)
        return

    session.is_speaking = True

    en_voice = DICTATION_EN_VOICES.get(session.accent, DICTATION_EN_VOICES["us"])

    for i in range(session.repeat_count):
        if not session.is_active or session.stop_requested:
            session.is_speaking = False
            return

        if session.mode == DictationMode.LISTEN_EN:
            # 英文单词模式：优先有道词典发音
            youdao_mp3 = await _download_youdao_audio(word.word, session.accent)
            if youdao_mp3:
                await _speak_youdao_word(conn, word.word, youdao_mp3, wait_after=0.3)
            else:
                # 回退到 EdgeTTS（应用语速）
                await _speak_and_wait(conn, word.word, wait_after=0.3,
                                      voice=en_voice, rate=session.speak_rate)
        else:
            # 中文释义模式：使用 EdgeTTS（应用语速）
            text = word.meaning or word.word
            await _speak_and_wait(conn, text, wait_after=0.3, rate=session.speak_rate)

        # 重复播报间的间隔（非最后一次重复时等待）
        if i < session.repeat_count - 1:
            try:
                await asyncio.sleep(session.repeat_interval)
            except asyncio.CancelledError:
                session.is_speaking = False
                return

    session.is_speaking = False

    if not session.is_active or session.stop_requested:
        return

    # 单词间隔等待（供学生默写）
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
