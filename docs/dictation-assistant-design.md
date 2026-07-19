# 小智听写助手 - 设计方案

## 一、背景与目标

小智听写助手是一个面向小朋友的英语听写训练功能。家长/老师通过后台从已有词汇表中挑选单词（或批量文本输入），生成一次听写任务；小智通过 edge-tts 高标准英/美式发音为小朋友报听写，自动识别回答正确率，正确给予夸奖，错误则记录并提示正确答案与例句，循环多轮直到全部通过。

### 核心特性

- 复用小智现有的 ASR → LLM/Tool → TTS 语音管道
- 支持两种听写模式：听英文答中文 / 听中文答英文
- 听中文答英文时支持三种可配置的考核方式：
  - `spelling_only`：仅字母拼读通过即可（回答 "b-i-g" 算通过）
  - `word_only`：仅整词读法通过即可（回答 "big" 算通过）
  - `both`：字母拼读和整词读法都通过才算（先拼读 b-i-g，再整词 big）
- 错误时播报正确答案（中英文 + 字母拼写）+ 简单例句 + 近义词/反义词
- 多轮循环：错误的单词进入下一轮，直到全部通过
- 后台可配置：听写模式、英/美式口音、单词间隔、播报次数、语速、是否播报例句、拼写考核方式等

## 二、整体架构

| 层级 | 技术栈 | 职责 |
|------|--------|------|
| **ESP32 设备端** | C++ | 音频采集/播放、WebSocket 通信（无需改动） |
| **Python 服务端** | Python + edge-tts | 听写会话状态机、答案判定、TTS 播报 |
| **Java 管理后台** | Spring Boot + MyBatis Plus | 听写任务配置、词汇查询、记录存储 |
| **Vue 前端** | Vue 3 + Element Plus | 任务配置页面、听写记录查看 |

### 数据流

```
用户语音："小智，开始听写"
  → ASR 识别
  → 意图识别 / function_call：start_dictation
  → 从 Java 后台拉取听写任务配置（含单词列表）
  → 创建听写会话状态机（DictationSession）
  → 通过 conn.tts 默认管道播报开场白

[循环：每个单词或词组]
  → TTS 播报单词（英文/中文，按模式）
  → 等待用户语音回答
  → ASR 识别用户回答
  → 答案判定：
       - 听英文答中文 → LLM 判断中文释义是否匹配
       - 听中文答英文 → 按配置的考核方式判定：
            · spelling_only：字母拼读通过即可（b-i-g）
            · word_only：整词读法通过即可（big）
            · both：先拼读 b-i-g，再整词 big，两者都通过才算
  → TTS 反馈：
       - 正确 → 随机夸奖语
       - 错误 → 正确答案（中英文+拼写）+ 例句 + 近义词/反义词
  → 间隔等待（可配置秒数）
  → 下一个单词

[一轮结束]
  → TTS 播报本轮结果（正确率、错误单词列表）
  → 若有错误单词 → 进入下一轮（只听写错误的）
  → 全部正确 → TTS 播报"全部通过"并保存记录

用户语音："停止听写" / "退出听写"
  → 保存已有听写记录到后台
  → 清除听写会话状态
```

## 三、数据库设计

### 3.1 复用现有词汇表

直接复用项目中已有的两张表（数据已导入）：

**`biz_vocabulary_books`**（词书表）：
- `id`、`name`、`code`、`description`、`total_words`、`sort_order`
- 已包含 81 本词书（人教版小学/初中/高中、四级、六级、考研、雅思、托福等）

**`biz_vocabularies`**（单词表）：
- `id`、`word`、`book_id`、`word_rank`、`usphone`、`ukphone`、`ukspeech`、`usspeech`、`content`(JSON)、`book_code`、`tid`、`word_id`
- `content` JSON 结构包含丰富信息：
  ```json
  {
    "word": {
      "content": {
        "trans": [{"pos": "n", "tranCn": "药房；配药学", "tranOther": "a shop where medicines are sold"}],
        "syno": {"synos": [{"pos": "n", "hwds": [{"w": "dispensary"}], "tran": "药房"}]},
        "antos": {"anto": [{"hwd": "inept"}]},
        "phrase": {"phrases": [{"pcontent": "...", "pcn": "..."}]},
        "sentence": {"sentences": [{"scontent": "...", "scn": "..."}]},
        "remMethod": {"val": "pharma(药) + cy → 药店"},
        "relWord": {"rels": [{"pos": "n", "words": [{"hwd": "pharmacist", "tran": "药剂师"}]}]}
      }
    }
  }
  ```

### 3.2 新增表

#### 听写任务表 `dict_task`

```sql
CREATE TABLE `dict_task` (
    `id` VARCHAR(32) NOT NULL COMMENT '任务ID',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `task_name` VARCHAR(200) NOT NULL COMMENT '任务名称',
    `book_id` BIGINT COMMENT '来源词书ID（可为空，表示手动输入）',
    `mode` VARCHAR(30) NOT NULL DEFAULT 'listen_en_answer_cn' COMMENT '听写模式：listen_en_answer_cn(听英文答中文) / listen_cn_answer_en(听中文答英文)',
    `accent` VARCHAR(10) NOT NULL DEFAULT 'us' COMMENT '口音：us(美式) / uk(英式)',
    `interval_seconds` DECIMAL(4,1) NOT NULL DEFAULT 5.0 COMMENT '单词间隔时间（秒）',
    `repeat_count` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '每个单词播报次数（1~3）',
    `speak_rate` INT NOT NULL DEFAULT 0 COMMENT '语速调整(-100~100)',
    `spelling_check_mode` VARCHAR(20) NOT NULL DEFAULT 'spelling_only' COMMENT '听中文答英文时的拼写考核方式：spelling_only(仅字母拼读b-i-g) / word_only(仅整词big) / both(两者都要)',
    `show_example` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否播报例句：0否 1是',
    `example_translate` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否翻译例句：0否 1是',
    `show_synonym` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '错误时是否提示近义词/反义词：0否 1是',
    `max_rounds` INT UNSIGNED NOT NULL DEFAULT 10 COMMENT '最大循环轮数',
    `answer_timeout` INT UNSIGNED NOT NULL DEFAULT 15 COMMENT '单个单词回答超时时间（秒）',
    `words_json` JSON COMMENT '手动输入的单词列表JSON（非词书来源时使用，格式：[{"word":"apple","meaning":"苹果"}]）',
    `selected_word_ids` JSON COMMENT '从词书挑选的单词ID列表（biz_vocabularies.id数组）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
    `sort` INT UNSIGNED DEFAULT 0 COMMENT '排序',
    `creator` BIGINT COMMENT '创建者',
    `create_date` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updater` BIGINT COMMENT '更新者',
    `update_date` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_book_id` (`book_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='听写任务配置';
```

#### 听写记录表 `dict_record`

```sql
CREATE TABLE `dict_record` (
    `id` VARCHAR(32) NOT NULL COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `device_id` VARCHAR(100) COMMENT '设备ID',
    `task_id` VARCHAR(32) NOT NULL COMMENT '任务ID',
    `task_name` VARCHAR(200) COMMENT '任务名称（冗余）',
    `total_words` INT UNSIGNED NOT NULL COMMENT '总单词数',
    `total_rounds` INT UNSIGNED NOT NULL COMMENT '总轮数',
    `final_accuracy` DECIMAL(5,2) COMMENT '最终正确率(%)',
    `result_json` JSON COMMENT '详细结果JSON：每轮每词的对错详情',
    `start_time` DATETIME COMMENT '开始时间',
    `end_time` DATETIME COMMENT '结束时间',
    `duration_seconds` INT UNSIGNED COMMENT '听写时长（秒）',
    `create_date` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='听写记录';
```

### 3.3 Liquibase 变更集

在 `db.changelog-master.yaml` 末尾追加：

```yaml
  - changeSet:
      id: 202607201000
      author: dictation
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202607201000_dict_task.sql
  - changeSet:
      id: 202607201001
      author: dictation
      changes:
        - sqlFile:
            encoding: utf8
            path: classpath:db/changelog/202607201001_dict_record.sql
```

## 四、Python 服务端设计（核心）

### 4.1 听写会话状态机

在 `ConnectionHandler` 中新增听写会话状态。修改 `core/connection.py`：

```python
# 在 ConnectionHandler.__init__ 中新增
self.dictation_session = None  # DictationSession 实例，None 表示未在听写
self.dictation_answer_timeout_task = None  # 回答超时任务
```

新建 `core/handle/dictationHandler.py`：

```python
import json
import asyncio
import time
import random
import re
from enum import Enum
from typing import Dict, List, Optional
from dataclasses import dataclass, field
from config.logger import setup_logging
from core.providers.tts.dto.dto import TTSMessageDTO, SentenceType, ContentType

TAG = __name__
logger = setup_logging()


class DictationMode(Enum):
    LISTEN_EN_ANSWER_CN = "listen_en_answer_cn"   # 听英文答中文
    LISTEN_CN_ANSWER_EN = "listen_cn_answer_en"   # 听中文答英文


class SpellingCheckMode(Enum):
    """听中文答英文时的拼写考核方式"""
    SPELLING_ONLY = "spelling_only"   # 仅字母拼读通过即可（b-i-g）
    WORD_ONLY = "word_only"           # 仅整词读法通过即可（big）
    BOTH = "both"                     # 字母拼读和整词读法都要通过（先 b-i-g，再 big）


class WordState(Enum):
    PENDING = "pending"       # 待听写
    CORRECT = "correct"       # 正确
    WRONG = "wrong"           # 错误


@dataclass
class DictationWord:
    """听写单词数据"""
    word: str                          # 英文单词
    meaning: str                       # 中文释义
    phonetic_us: str = ""              # 美式音标
    phonetic_uk: str = ""              # 英式音标
    example_sentence: str = ""         # 英文例句
    example_translation: str = ""      # 例句中文翻译
    synonyms: List[str] = None         # 近义词
    antonyms: List[str] = None         # 反义词
    state: WordState = WordState.PENDING
    wrong_count: int = 0
    user_answers: list = field(default_factory=list)
    # both 模式专用：记录阶段性通过状态
    spelling_passed: bool = False      # 字母拼读是否已通过
    word_passed: bool = False          # 整词读法是否已通过


@dataclass
class DictationSession:
    """听写会话状态机"""
    task_id: str
    task_name: str
    mode: DictationMode
    accent: str                    # us / uk
    interval_seconds: float        # 单词间隔
    repeat_count: int              # 每词播报次数
    speak_rate: int                # 语速
    spelling_check_mode: SpellingCheckMode = SpellingCheckMode.SPELLING_ONLY  # 拼写考核方式
    show_example: bool             # 是否播报例句
    example_translate: bool        # 是否翻译例句
    show_synonym: bool             # 错误时是否提示近义词/反义词
    max_rounds: int
    answer_timeout: int            # 单词回答超时（秒）
    words: List[DictationWord] = field(default_factory=list)

    current_round: int = 1
    current_word_index: int = 0
    current_round_words: List[DictationWord] = field(default_factory=list)
    is_active: bool = True
    is_speaking_word: bool = False   # 正在播报单词
    is_waiting_answer: bool = False  # 正在等待用户回答
    # both 模式专用：当前正在等待的回答阶段（spelling / word）
    current_check_stage: str = ""    # "spelling" 或 "word"
    start_time: float = 0.0

    def __post_init__(self):
        self.current_round_words = [w for w in self.words if w.state == WordState.PENDING]
        self.start_time = time.time()

    def get_current_word(self) -> Optional[DictationWord]:
        if self.current_word_index < len(self.current_round_words):
            return self.current_round_words[self.current_word_index]
        return None

    def advance_to_next_word(self) -> bool:
        """前进到下一个单词，返回 False 表示本轮结束"""
        self.current_word_index += 1
        return self.current_word_index < len(self.current_round_words)

    def start_next_round(self) -> bool:
        """开始下一轮（只包含错误的词）"""
        wrong_words = [w for w in self.words if w.state == WordState.WRONG]
        if not wrong_words or self.current_round >= self.max_rounds:
            return False
        self.current_round += 1
        for w in wrong_words:
            w.state = WordState.PENDING
            # 重置 both 模式的阶段状态
            w.spelling_passed = False
            w.word_passed = False
        self.current_round_words = wrong_words
        self.current_word_index = 0
        self.current_check_stage = ""
        return True

    def get_accuracy(self) -> float:
        correct = sum(1 for w in self.words if w.state == WordState.CORRECT)
        return (correct / len(self.words) * 100) if self.words else 0

    def to_result_json(self) -> dict:
        return {
            "taskId": self.task_id,
            "taskName": self.task_name,
            "mode": self.mode.value,
            "spellingCheckMode": self.spelling_check_mode.value,
            "totalRounds": self.current_round,
            "finalAccuracy": round(self.get_accuracy(), 2),
            "words": [
                {
                    "word": w.word,
                    "meaning": w.meaning,
                    "state": w.state.value,
                    "wrongCount": w.wrong_count,
                    "userAnswers": w.user_answers,
                }
                for w in self.words
            ]
        }
```

### 4.2 核心处理器（复用默认 TTS 管道）

`core/handle/dictationHandler.py` 续：

```python
async def _speak(conn, text: str):
    """通过连接默认的 TTS 管道播报文本（复用 conn.tts）"""
    sentence_id = str(__import__('uuid').uuid4().hex)
    conn.sentence_id = sentence_id

    # 发送 FIRST 标记，开启一轮 TTS
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=sentence_id,
            sentence_type=SentenceType.FIRST,
            content_type=ContentType.ACTION,
        )
    )
    # 发送文本内容
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=sentence_id,
            sentence_type=SentenceType.MIDDLE,
            content_type=ContentType.TEXT,
            content_detail=text,
        )
    )
    # 发送 LAST 标记，结束本轮 TTS
    conn.tts.tts_text_queue.put(
        TTSMessageDTO(
            sentence_id=sentence_id,
            sentence_type=SentenceType.LAST,
            content_type=ContentType.ACTION,
        )
    )
    # 记录到对话历史
    from core.utils.dialogue import Message
    conn.tts.store_tts_text(sentence_id, text)
    conn.dialogue.put(Message(role="assistant", content=text))


async def _speak_and_wait(conn, text: str, wait_after: float = 0.5):
    """播报文本并等待一段时间（让 TTS 队列消费完）"""
    await _speak(conn, text)
    # 等待 TTS 队列处理完成（粗略估计：每字约 0.15 秒）
    estimated_duration = max(1.0, len(text) * 0.15)
    await asyncio.sleep(estimated_duration + wait_after)


async def start_dictation(conn, task_config: dict):
    """启动听写会话"""
    from plugins_func.register import ActionResponse, Action

    words = []
    for item in task_config.get("words", []):
        words.append(DictationWord(
            word=item["word"],
            meaning=item["meaning"],
            phonetic_us=item.get("phonetic_us", ""),
            phonetic_uk=item.get("phonetic_uk", ""),
            example_sentence=item.get("example_sentence", ""),
            example_translation=item.get("example_translation", ""),
            synonyms=item.get("synonyms", []),
            antonyms=item.get("antonyms", []),
        ))

    if not words:
        return ActionResponse(Action.RESPONSE, response="没有找到听写单词，请先配置听写任务哦")

    session = DictationSession(
        task_id=task_config["taskId"],
        task_name=task_config["taskName"],
        mode=DictationMode(task_config.get("mode", "listen_en_answer_cn")),
        accent=task_config.get("accent", "us"),
        interval_seconds=float(task_config.get("intervalSeconds", 5.0)),
        repeat_count=int(task_config.get("repeatCount", 1)),
        speak_rate=int(task_config.get("speakRate", 0)),
        spelling_check_mode=SpellingCheckMode(task_config.get("spellingCheckMode", "spelling_only")),
        show_example=bool(task_config.get("showExample", False)),
        example_translate=bool(task_config.get("exampleTranslate", False)),
        show_synonym=bool(task_config.get("showSynonym", False)),
        max_rounds=int(task_config.get("maxRounds", 10)),
        answer_timeout=int(task_config.get("answerTimeout", 15)),
        words=words,
    )

    conn.dictation_session = session

    # 播报开场白
    opening = f"听写开始！本次听写共有{len(words)}个单词。"
    if session.mode == DictationMode.LISTEN_EN_ANSWER_CN:
        opening += "我会读英文，你来说中文意思。"
    else:
        opening += "我会说中文意思，你来拼英文单词。"
        if session.spelling_check_mode == SpellingCheckMode.SPELLING_ONLY:
            opening += "请用字母拼读的方式回答，比如 b-i-g。"
        elif session.spelling_check_mode == SpellingCheckMode.WORD_ONLY:
            opening += "请直接读出整个单词，比如 big。"
        else:  # BOTH
            opening += "每个词需要先用字母拼读，再读整个单词，两者都正确才算通过。"
    opening += "准备好了吗？我们开始吧！"

    await _speak_and_wait(conn, opening, wait_after=1.0)

    # 开始第一个单词
    await _speak_current_word(conn)

    return ActionResponse(Action.NONE)  # 不再走普通聊天流程


async def _speak_current_word(conn):
    """播报当前单词"""
    session = conn.dictation_session
    if not session or not session.is_active:
        return

    word = session.get_current_word()
    if not word:
        await _finish_round(conn)
        return

    session.is_speaking_word = True
    session.is_waiting_answer = False

    # 序号提示
    await _speak_and_wait(conn, f"第{session.current_word_index + 1}个词。", wait_after=0.3)

    if session.mode == DictationMode.LISTEN_EN_ANSWER_CN:
        # 听英文答中文：读英文单词（可重复）
        for i in range(session.repeat_count):
            await _speak_and_wait(conn, word.word, wait_after=0.5)
            if i < session.repeat_count - 1:
                await asyncio.sleep(0.8)
    else:
        # 听中文答英文：说中文释义
        await _speak_and_wait(conn, word.meaning, wait_after=0.5)
        # both 模式：提示当前阶段
        if session.spelling_check_mode == SpellingCheckMode.BOTH:
            if not word.spelling_passed:
                session.current_check_stage = "spelling"
                await _speak_and_wait(conn, "请先用字母拼读。", wait_after=0.3)
            elif not word.word_passed:
                session.current_check_stage = "word"
                await _speak_and_wait(conn, "拼读正确！现在请读出整个单词。", wait_after=0.3)
        elif session.spelling_check_mode == SpellingCheckMode.SPELLING_ONLY:
            session.current_check_stage = "spelling"
        else:  # WORD_ONLY
            session.current_check_stage = "word"

    # 播报例句（如果配置了）
    if session.show_example and word.example_sentence:
        await _speak_and_wait(conn, word.example_sentence, wait_after=0.3)
        if session.example_translate and word.example_translation:
            await _speak_and_wait(conn, word.example_translation, wait_after=0.3)

    session.is_speaking_word = False
    session.is_waiting_answer = True

    # 启动回答超时任务
    if conn.dictation_answer_timeout_task:
        conn.dictation_answer_timeout_task.cancel()
    conn.dictation_answer_timeout_task = asyncio.create_task(
        _handle_answer_timeout(conn, session.answer_timeout)
    )


async def _handle_answer_timeout(conn, timeout_seconds: int):
    """回答超时处理"""
    await asyncio.sleep(timeout_seconds)
    session = conn.dictation_session
    if session and session.is_waiting_answer:
        logger.bind(tag=TAG).info("听写回答超时，自动判错")
        await _process_answer(conn, "")


async def _process_answer(conn, user_answer: str):
    """处理用户回答"""
    session = conn.dictation_session
    if not session or not session.is_active:
        return

    # 取消超时任务
    if conn.dictation_answer_timeout_task:
        conn.dictation_answer_timeout_task.cancel()
        conn.dictation_answer_timeout_task = None

    word = session.get_current_word()
    if not word:
        return

    session.is_waiting_answer = False
    word.user_answers.append(user_answer)

    # 判定答案
    if session.mode == DictationMode.LISTEN_EN_ANSWER_CN:
        # 听英文答中文：LLM 判定
        is_correct = await _check_chinese_answer_with_llm(conn, word, user_answer)
        await _apply_result(conn, word, session, is_correct)
    else:
        # 听中文答英文：按拼写考核方式判定
        check_mode = session.spelling_check_mode
        stage = session.current_check_stage

        if check_mode == SpellingCheckMode.SPELLING_ONLY:
            # 仅字母拼读
            is_correct = await _check_spelling(conn, word.word, user_answer)
            await _apply_result(conn, word, session, is_correct)

        elif check_mode == SpellingCheckMode.WORD_ONLY:
            # 仅整词读法
            is_correct = _check_whole_word(word.word, user_answer)
            await _apply_result(conn, word, session, is_correct)

        else:  # BOTH：分阶段考核
            if stage == "spelling":
                spelling_ok = await _check_spelling(conn, word.word, user_answer)
                if spelling_ok:
                    word.spelling_passed = True
                    # 进入整词阶段
                    session.current_check_stage = "word"
                    session.is_waiting_answer = True
                    await _speak_and_wait(conn, "拼读正确！现在请读出整个单词。", wait_after=0.5)
                    # 重新启动超时任务等待整词回答
                    conn.dictation_answer_timeout_task = asyncio.create_task(
                        _handle_answer_timeout(conn, session.answer_timeout)
                    )
                    return  # 不进入下一词，等待整词回答
                else:
                    # 拼读阶段失败，整词也失败
                    await _apply_result(conn, word, session, False)
            elif stage == "word":
                word_ok = _check_whole_word(word.word, user_answer)
                if word_ok:
                    word.word_passed = True
                    await _apply_result(conn, word, session, True)
                else:
                    # 整词阶段失败
                    await _apply_result(conn, word, session, False)


async def _apply_result(conn, word: DictationWord, session: DictationSession, is_correct: bool):
    """应用判定结果：播报反馈、推进状态"""
    if is_correct:
        word.state = WordState.CORRECT
        praises = [
            "太棒了！回答正确！",
            "非常好！你真厉害！",
            "完全正确！继续加油！",
            "真聪明！答对了！",
            "了不起！答对了！",
        ]
        await _speak_and_wait(conn, random.choice(praises), wait_after=0.3)
    else:
        word.state = WordState.WRONG
        word.wrong_count += 1
        # 错误提示：正确答案（中英文+拼写）+ 例句 + 近义词/反义词
        await _speak_wrong_feedback(conn, word, session)

    # 重置阶段状态（both 模式专用）
    word.spelling_passed = False
    word.word_passed = False
    session.current_check_stage = ""

    # 间隔等待
    await asyncio.sleep(session.interval_seconds)

    # 下一个单词
    if session.advance_to_next_word():
        await _speak_current_word(conn)
    else:
        await _finish_round(conn)


async def _speak_wrong_feedback(conn, word: DictationWord, session: DictationSession):
    """播报错误反馈：正确答案 + 例句 + 近义词/反义词"""
    # 1. 鼓励语 + 正确答案
    feedback_parts = []
    feedback_parts.append("没关系，加油哦！")

    if session.mode == DictationMode.LISTEN_EN_ANSWER_CN:
        # 听英文答中文：提示中文释义
        feedback_parts.append(f"正确答案是：{word.meaning}。英文是 {word.word}。")
    else:
        # 听中文答英文：提示英文单词 + 字母拼写
        spelled = "-".join(list(word.word))
        feedback_parts.append(f"正确的单词是：{word.word}。字母拼写是：{spelled}。中文意思是：{word.meaning}。")

    await _speak_and_wait(conn, "。".join(feedback_parts), wait_after=0.5)

    # 2. 例句（如果词汇表有且配置了播报例句）
    if session.show_example and word.example_sentence:
        await _speak_and_wait(conn, f"例句：{word.example_sentence}", wait_after=0.3)
        if word.example_translation:
            await _speak_and_wait(conn, word.example_translation, wait_after=0.3)

    # 3. 近义词/反义词（如果配置了提示）
    if session.show_synonym:
        tips = []
        if word.synonyms:
            tips.append(f"近义词有：{'、'.join(word.synonyms[:3])}")
        if word.antonyms:
            tips.append(f"反义词有：{'、'.join(word.antonyms[:3])}")
        if tips:
            await _speak_and_wait(conn, "。".join(tips), wait_after=0.3)


def _check_whole_word(correct_word: str, user_answer: str) -> bool:
    """检查整词读法：用户直接读出整个单词（big）"""
    answer = user_answer.strip().lower()
    correct = correct_word.strip().lower()

    # 精确匹配
    if answer == correct:
        return True

    # 容错：忽略空格（ASR 可能多识别空格）
    if answer.replace(" ", "") == correct.replace(" ", ""):
        return True

    # 容错：编辑距离 ≤ 1（轻微发音误差）
    if len(answer) > 2 and _levenshtein_distance(answer, correct) <= 1:
        return True

    return False


def _levenshtein_distance(s1: str, s2: str) -> int:
    """计算编辑距离"""
    if len(s1) < len(s2):
        return _levenshtein_distance(s2, s1)
    if len(s2) == 0:
        return len(s1)
    prev_row = range(len(s2) + 1)
    for i, c1 in enumerate(s1):
        curr_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = prev_row[j + 1] + 1
            deletions = curr_row[j] + 1
            substitutions = prev_row[j] + (c1 != c2)
            curr_row.append(min(insertions, deletions, substitutions))
        prev_row = curr_row
    return prev_row[-1]


async def _check_spelling(conn, correct_word: str, user_answer: str) -> bool:
    """检查字母拼读：用户逐字母读（b-i-g / b i g / bee eye gee）
    先做规则匹配（去分隔符比较），未命中再用 LLM 判定。
    """
    answer = user_answer.strip().lower()
    correct = correct_word.strip().lower()

    if not answer:
        return False

    # 规则匹配：去掉分隔符后直接比较
    answer_no_sep = re.sub(r'[\s\-,.，。、]+', '', answer)
    correct_letters = ''.join(list(correct))
    if answer_no_sep == correct_letters:
        return True

    # 未命中规则，交给 LLM 判定
    return await _check_spelling_with_llm(conn, correct_word, user_answer)


async def _check_spelling_with_llm(conn, correct_word: str, user_answer: str) -> bool:
    """使用 LLM 判断用户的回答是否为正确的字母拼读。
    处理 ASR 将字母识别为谐音词的情况（如 b→bee, c→see, i→eye）。
    """
    prompt = f"""你是一个英语听写判卷助手。请判断用户的回答是否为单词 "{correct_word}" 的正确字母拼读。

单词：{correct_word}
字母拆分：{'-'.join(list(correct_word))}
用户的回答：{user_answer}

判断规则：
1. 用户可能是逐字母拼读（如 b-i-g、b i g、bee eye gee 都算正确）
2. ASR 语音识别可能把字母识别成谐音词，如 b→bee/be, c→see/sea, i→eye, o→oh, r→are, u→you, y→why 等，这种情况算正确
3. 用户回答的字母顺序和数量必须与单词的字母完全一致
4. 忽略大小写、空格、连字符、逗号等分隔符差异

请只回答一个JSON：{{"correct": true}} 或 {{"correct": false}}
不要输出其他任何内容。"""

    try:
        future = asyncio.run_coroutine_threadsafe(
            conn.llm.response_text_with_prompt(prompt),
            conn.loop,
        )
        result = future.result(timeout=10)
        result = result.strip()
        match = re.search(r'\{[^}]*"correct"[^}]*\}', result)
        if match:
            data = json.loads(match.group())
            return data.get("correct", False)
        return "true" in result.lower() or "正确" in result
    except Exception as e:
        logger.bind(tag=TAG).error(f"LLM 判定字母拼读失败: {e}")
        # 兜底：规则匹配（去分隔符比较）
        answer_no_sep = re.sub(r'[\s\-,.，。、]+', '', user_answer.lower())
        correct_letters = ''.join(list(correct_word.lower()))
        return answer_no_sep == correct_letters


async def _check_chinese_answer_with_llm(conn, word: DictationWord, user_answer: str) -> bool:
    """使用 LLM 判断中文回答是否匹配单词释义"""
    prompt = f"""你是一个英语听写判卷助手。请判断用户的中文回答是否表达了下面单词的正确含义。

单词：{word.word}
标准中文释义：{word.meaning}
用户的回答：{user_answer}

判断规则：
1. 用户的回答只需表达出标准释义的核心含义即可算正确
2. 允许同义词、近义词表达
3. 允许用户只说出释义的一部分关键词
4. 不要求完全一致，但核心含义必须对

请只回答一个JSON：{{"correct": true}} 或 {{"correct": false}}
不要输出其他任何内容。"""

    try:
        import asyncio
        future = asyncio.run_coroutine_threadsafe(
            conn.llm.response_text_with_prompt(prompt),
            conn.loop,
        )
        result = future.result(timeout=10)
        result = result.strip()
        # 提取 JSON
        import re
        match = re.search(r'\{[^}]*"correct"[^}]*\}', result)
        if match:
            data = json.loads(match.group())
            return data.get("correct", False)
        # 兜底：包含 true/正确 视为正确
        return "true" in result.lower() or "正确" in result
    except Exception as e:
        logger.bind(tag=TAG).error(f"LLM 判定答案失败: {e}")
        # 兜底：简单字符串匹配
        meaning = word.meaning.lower()
        answer = user_answer.lower()
        clean_answer = re.sub(r'[，。、！？\s]', '', answer)
        clean_meaning = re.sub(r'[，。、！？\s]', '', meaning)
        return clean_answer in clean_meaning or clean_meaning in clean_answer


async def _finish_round(conn):
    """完成一轮听写"""
    session = conn.dictation_session
    if not session:
        return

    correct_count = sum(1 for w in session.current_round_words if w.state == WordState.CORRECT)
    total = len(session.current_round_words)
    accuracy = (correct_count / total * 100) if total > 0 else 0

    # 播报本轮结果
    result_text = f"本轮听写结束！共{total}个词，答对了{correct_count}个，正确率{accuracy:.0f}%。"

    wrong_words = [w for w in session.current_round_words if w.state == WordState.WRONG]
    if wrong_words:
        if session.mode == DictationMode.LISTEN_EN_ANSWER_CN:
            wrong_list = "、".join(w.word for w in wrong_words)
            result_text += f"需要再练习的单词有：{wrong_list}。"
        else:
            wrong_list = "、".join(w.word for w in wrong_words)
            result_text += f"需要再练习的单词有：{wrong_list}。"

    await _speak_and_wait(conn, result_text, wait_after=1.0)

    # 检查是否需要下一轮
    if session.start_next_round():
        await asyncio.sleep(1.5)
        await _speak_and_wait(
            conn,
            f"开始第{session.current_round}轮，只听写刚才错误的{len(session.current_round_words)}个词。",
            wait_after=1.0
        )
        await _speak_current_word(conn)
    else:
        await _finish_dictation(conn)


async def _finish_dictation(conn):
    """结束整个听写会话"""
    session = conn.dictation_session
    if not session:
        return

    accuracy = session.get_accuracy()

    if accuracy == 100:
        closing = "太棒了！全部正确，你真了不起！听写结束，再见！"
    elif accuracy >= 80:
        closing = f"真不错！正确率{accuracy:.0f}%，继续加油！听写结束。"
    elif accuracy >= 60:
        closing = f"还不错哦，正确率{accuracy:.0f}%，多练习一定能更好！听写结束。"
    else:
        closing = f"别灰心，正确率{accuracy:.0f}%，我们下次再来！听写结束。"

    await _speak_and_wait(conn, closing, wait_after=0.5)

    # 保存记录到后台
    await _save_dictation_record(conn, session)

    # 清除听写会话
    conn.dictation_session = None


async def _save_dictation_record(conn, session: DictationSession):
    """保存听写记录到 Java 后台"""
    try:
        from config.manage_api_client import get_manage_api
        api = get_manage_api(conn.config)
        await api.post("/api/dict/record", {
            "taskId": session.task_id,
            "deviceId": conn.device_id,
            "totalWords": len(session.words),
            "totalRounds": session.current_round,
            "finalAccuracy": round(session.get_accuracy(), 2),
            "resultJson": json.dumps(session.to_result_json(), ensure_ascii=False),
            "startTime": session.start_time,
        })
    except Exception as e:
        logger.bind(tag=TAG).error(f"保存听写记录失败: {e}")


async def handle_dictation_answer(conn, text: str) -> bool:
    """处理听写中的用户语音回答。
    返回 True 表示已处理（不走普通聊天流程），False 表示交给普通流程。
    """
    session = conn.dictation_session
    if not session or not session.is_active:
        return False

    # 检查退出指令
    exit_keywords = ["停止听写", "退出听写", "不听了", "结束听写", "停止"]
    if any(kw in text for kw in exit_keywords):
        await _speak_and_wait(conn, "好的，听写结束！", wait_after=0.3)
        if session:
            await _save_dictation_record(conn, session)
        conn.dictation_session = None
        return True

    # 检查重复播报请求
    repeat_keywords = ["再说一遍", "重复一次", "再说一次", "再读一次", "没听清"]
    if any(kw in text for kw in repeat_keywords):
        await _speak_current_word(conn)
        return True

    # 等待回答状态时处理用户回答
    if session.is_waiting_answer:
        await _process_answer(conn, text)
        return True

    # 正在播报单词时忽略用户说话
    if session.is_speaking_word:
        return True

    return False
```

### 4.3 插件注册

新建 `plugins_func/functions/dictation.py`：

```python
import json
from plugins_func.register import register_function, ToolType, ActionResponse, Action

dictation_function_desc = {
    "type": "function",
    "function": {
        "name": "start_dictation",
        "description": (
            "启动英语听写任务。当用户要求进行听写、单词听写、报听写时调用此功能。"
            "用户可以说'开始听写'、'我要听写'、'报听写'等。"
            "系统会根据后台配置的听写任务，逐个播报单词并等待用户回答。"
        ),
        "parameters": {
            "type": "object",
            "properties": {
                "task_name": {
                    "type": "string",
                    "description": "听写任务名称（如果用户指定了具体任务名），为空则使用默认任务",
                },
            },
            "required": [],
        },
    },
}


@register_function("start_dictation", dictation_function_desc, ToolType.SYSTEM_CTL)
async def start_dictation(conn, task_name=None):
    """启动听写任务"""
    from core.handle.dictationHandler import start_dictation as _start_dictation

    # 从 Java 后台获取听写任务配置
    task_config = await _fetch_dictation_task(conn, task_name)
    if not task_config:
        return ActionResponse(
            Action.RESPONSE,
            response="还没有配置听写任务哦，请先在后台配置听写任务吧！"
        )

    return await _start_dictation(conn, task_config)


async def _fetch_dictation_task(conn, task_name=None):
    """从 Java 后台 API 获取听写任务配置（含单词列表）"""
    try:
        from config.manage_api_client import get_manage_api
        api = get_manage_api(conn.config)
        url = "/api/dict/task/active"
        if task_name:
            url += f"?taskName={task_name}"
        result = await api.get(url)
        return result
    except Exception as e:
        from config.logger import setup_logging
        logger = setup_logging()
        logger.bind(tag="dictation").error(f"获取听写任务失败: {e}")
        return None
```

### 4.4 接入 ConnectionHandler 消息流

在 `core/handle/textHandle.py` 的 `handleTextMessage` 中（或在 ASR 文本回调处）插入听写会话优先判断：

```python
async def handleTextMessage(conn, message):
    """处理文本消息"""
    try:
        data = json.loads(message) if isinstance(message, str) else message
        # ... 原有逻辑 ...

        # === 新增：听写会话优先处理 ===
        if conn.dictation_session and conn.dictation_session.is_active:
            # 提取文本内容
            text = data.get("content", "") or data.get("text", "")
            if text:
                from core.handle.dictationHandler import handle_dictation_answer
                handled = await handle_dictation_answer(conn, text)
                if handled:
                    return  # 不走普通聊天流程
        # === 听写会话处理结束 ===

        # ... 原有消息处理逻辑 ...
    except Exception as e:
        conn.logger.bind(tag=TAG).error(f"处理文本消息失败: {e}")
```

同样，在 ASR 识别出文本后的回调（`core/handle/receiveAudioHandle.py` 或 `sendAudioHandle.py`）中插入相同的判断。

## 五、Java 后台 API 设计

### 5.1 模块结构

```
xiaozhi.modules.dict/
├── controller/
│   ├── DictTaskController.java          # 听写任务 CRUD + 获取生效任务
│   ├── DictRecordController.java        # 听写记录查询 + 保存
│   └── DictVocabularyController.java    # 词汇查询（查词书/单词）
├── service/
│   ├── DictTaskService.java
│   ├── DictRecordService.java
│   └── DictVocabularyService.java
├── entity/
│   ├── DictTaskEntity.java
│   ├── DictRecordEntity.java
│   ├── BizVocabularyEntity.java         # 映射 biz_vocabularies 表
│   └── BizVocabularyBookEntity.java     # 映射 biz_vocabulary_books 表
├── dao/
│   ├── DictTaskDao.java
│   ├── DictRecordDao.java
│   ├── BizVocabularyDao.java
│   └── BizVocabularyBookDao.java
└── vo/
    ├── DictTaskVO.java                  # 含单词列表的任务对象
    └── DictVocabularyVO.java            # 解析 content JSON 后的单词对象
```

### 5.2 核心 API 端点

#### 5.2.1 词汇查询（供前端选择单词）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dict/books/list` | 获取所有词书列表 |
| GET | `/api/dict/books/{bookId}/words` | 分页获取词书下的单词（含 content 解析） |
| GET | `/api/dict/books/{bookId}/words/search` | 按单词搜索 |
| POST | `/api/dict/words/parse-content` | 解析 content JSON 为结构化 VO（前端展示用） |

#### 5.2.2 听写任务管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dict/task/page` | 任务分页查询（后台用） |
| GET | `/api/dict/task/{id}` | 获取任务详情（含单词列表） |
| POST | `/api/dict/task` | 创建听写任务 |
| PUT | `/api/dict/task` | 更新听写任务 |
| DELETE | `/api/dict/task` | 删除听写任务 |
| GET | `/api/dict/task/active` | **获取当前生效的听写任务（Python 端调用，返回完整单词列表）** |

#### 5.2.3 听写记录

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dict/record/page` | 记录分页查询（后台用） |
| GET | `/api/dict/record/{id}` | 记录详情（含每轮每词对错） |
| POST | `/api/dict/record` | **保存听写记录（Python 端调用）** |

### 5.3 `/api/dict/task/active` 返回结构

Python 端调用此接口获取听写任务，返回的 JSON 结构需包含完整的单词信息：

```json
{
    "taskId": "abc123",
    "taskName": "三年级上册 Unit1 听写",
    "mode": "listen_cn_answer_en",
    "accent": "us",
    "intervalSeconds": 5.0,
    "repeatCount": 1,
    "speakRate": 0,
    "spellingCheckMode": "both",
    "showExample": true,
    "exampleTranslate": true,
    "showSynonym": true,
    "maxRounds": 10,
    "answerTimeout": 15,
    "words": [
        {
            "word": "apple",
            "meaning": "苹果",
            "phonetic_us": "ˈæpl",
            "phonetic_uk": "ˈæp(ə)l",
            "example_sentence": "I like to eat apples.",
            "example_translation": "我喜欢吃苹果。",
            "synonyms": ["fruit"],
            "antonyms": []
        }
    ]
}
```

**`spellingCheckMode` 字段说明**（仅 `mode=listen_cn_answer_en` 时生效）：

| 值 | 含义 | 通过条件 |
|----|------|----------|
| `spelling_only` | 仅字母拼读 | 用户回答 "b-i-g" / "b i g" / "bee eye gee" 任一形式通过即可 |
| `word_only` | 仅整词读法 | 用户回答 "big" 通过即可（含 1 字母编辑距离容错） |
| `both` | 两者都要 | 用户需先字母拼读通过，再整词读法通过，两阶段都正确才算通过 |

### 5.4 content JSON 解析

`BizVocabularyEntity` 的 `content` 字段是嵌套 JSON，需在 Service 层解析为 `DictVocabularyVO`：

```java
public class DictVocabularyVO {
    private String word;
    private String phoneticUs;
    private String phoneticUk;
    private String meaning;              // 中文释义（取 trans[0].tranCn）
    private String meaningEn;            // 英文释义（取 trans[0].tranOther）
    private String exampleSentence;      // 例句（取 sentence.sentences[0].scontent）
    private String exampleTranslation;   // 例句中文（取 sentence.sentences[0].scn）
    private List<String> synonyms;       // 近义词（从 syno.synos[].hwds[].w 提取）
    private List<String> antonyms;       // 反义词（从 antos.anto[].hwd 提取）
    private String remMethod;            // 记忆方法
}
```

解析工具方法示例：

```java
public static DictVocabularyVO parseContent(String contentJson, String word, 
                                             String usphone, String ukphone) {
    DictVocabularyVO vo = new DictVocabularyVO();
    vo.setWord(word);
    vo.setPhoneticUs(usphone);
    vo.setPhoneticUk(ukphone);
    
    try {
        JSONObject root = JSONUtil.parseObj(contentJson);
        JSONObject wordObj = root.getJSONObject("word");
        JSONObject content = wordObj.getJSONObject("content");
        
        // 中文释义
        JSONArray trans = content.getJSONArray("trans");
        if (trans != null && !trans.isEmpty()) {
            JSONObject firstTrans = trans.getJSONObject(0);
            vo.setMeaning(firstTrans.getStr("tranCn"));
            vo.setMeaningEn(firstTrans.getStr("tranOther"));
        }
        
        // 例句
        JSONObject sentence = content.getJSONObject("sentence");
        if (sentence != null) {
            JSONArray sentences = sentence.getJSONArray("sentences");
            if (sentences != null && !sentences.isEmpty()) {
                JSONObject firstSentence = sentences.getJSONObject(0);
                vo.setExampleSentence(firstSentence.getStr("scontent"));
                vo.setExampleTranslation(firstSentence.getStr("scn"));
            }
        }
        
        // 近义词
        JSONObject syno = content.getJSONObject("syno");
        if (syno != null) {
            JSONArray synos = syno.getJSONArray("synos");
            if (synos != null) {
                List<String> synonyms = new ArrayList<>();
                for (int i = 0; i < synos.size(); i++) {
                    JSONArray hwds = synos.getJSONObject(i).getJSONArray("hwds");
                    for (int j = 0; j < hwds.size(); j++) {
                        synonyms.add(hwds.getJSONObject(j).getStr("w"));
                    }
                }
                vo.setSynonyms(synonyms);
            }
        }
        
        // 反义词
        JSONObject antos = content.getJSONObject("antos");
        if (antos != null) {
            JSONArray anto = antos.getJSONArray("anto");
            if (anto != null) {
                List<String> antonyms = new ArrayList<>();
                for (int i = 0; i < anto.size(); i++) {
                    antonyms.add(anto.getJSONObject(i).getStr("hwd"));
                }
                vo.setAntonyms(antonyms);
            }
        }
        
        vo.setRemMethod(content.getStr("remMethod"));
    } catch (Exception e) {
        log.warn("解析词汇 content 失败: {}", word, e);
    }
    
    return vo;
}
```

## 六、Vue 前端页面设计（Vue 3 + Element Plus）

### 6.1 技术栈说明

前端采用 **Vue 3 + Element Plus**，使用 Composition API (`<script setup>`) 编写。与 Vue 2 + Element UI 的主要差异：

| 项目 | Vue 2 + Element UI | Vue 3 + Element Plus |
|------|--------------------|--------------------|
| 组件注册 | `Vue.use(ElementUI)` | `app.use(ElementPlus)` |
| 表单 ref | `this.$refs.form` | `const formRef = ref()` |
| 弹窗 visible | `:visible.sync` | `v-model` |
| 事件绑定 | `@click.native` | `@click` |
| 表单校验 | callback 风格 | Promise 风格 `formRef.value.validate()` |
| 图标 | `el-icon` 字体 | `@element-plus/icons-vue` 按需引入 |
| 响应式 | `data()` + `this.$set` | `ref()` / `reactive()` |

### 6.2 新增路由

```javascript
// router/index.js 新增（Vue 3 写法）
const routes = [
    // ... 已有路由
    {
        path: '/dict-task-management',
        name: 'DictTaskManagement',
        component: () => import('../views/DictTaskManagement.vue')
    },
    {
        path: '/dict-record-management',
        name: 'DictRecordManagement',
        component: () => import('../views/DictRecordManagement.vue')
    },
]
```

### 6.3 听写任务管理页 `DictTaskManagement.vue`

页面布局：

```
┌──────────────────────────────────────────────────────┐
│  听写任务管理                          [+ 新建任务]    │
├──────────────────────────────────────────────────────┤
│  搜索: [任务名________] [状态▼] [查询] [重置]         │
├──────────────────────────────────────────────────────┤
│  任务名称        │ 来源      │ 模式     │ 状态 │ 操作  │
│  三年级Unit1    │ 人教三上  │ 听英答中 │ 启用 │ 编辑/删除│
│  四年级复习      │ 手动输入  │ 听中答英 │ 禁用 │ 编辑/删除│
└──────────────────────────────────────────────────────┘
```

**新建/编辑任务弹窗**：

```
┌──────────────────────────────────────────────────────┐
│  新建听写任务                                          │
├──────────────────────────────────────────────────────┤
│  任务名称*: [________________________]                │
│                                                       │
│  词汇来源: ○ 从词书选择  ○ 手动输入                   │
│                                                       │
│  [选择词书时]                                          │
│  词书: [人教版小学三年级上册 ▼]                        │
│  单词列表: [全选/反选] [已选 5/64 个]                 │
│  ┌─────────────────────────────────────────┐         │
│  │ ☑ apple   ☑ banana  ☐ cat   ☑ dog      │         │
│  │ ☐ egg     ☑ fish    ☐ goat  ☐ hat       │         │
│  └─────────────────────────────────────────┘         │
│                                                       │
│  [手动输入时]                                          │
│  单词列表 (每行一个: 单词 - 中文释义):                 │
│  ┌─────────────────────────────────────────┐         │
│  │ apple - 苹果                             │         │
│  │ banana - 香蕉                            │         │
│  │ big - 大的                               │         │
│  └─────────────────────────────────────────┘         │
│                                                       │
│  听写模式: ○ 听英文答中文  ○ 听中文答英文              │
│  口音:     ○ 美式  ○ 英式                             │
│                                                       │
│  [听中文答英文时显示]                                  │
│  拼写考核方式:                                         │
│    ○ 仅字母拼读 (回答 b-i-g 即可)                      │
│    ○ 仅整词读法 (回答 big 即可)                        │
│    ○ 两者都要 (先 b-i-g，再 big)                       │
│                                                       │
│  单词间隔: [───●───] 5.0 秒                           │
│  播报次数: [1] 次                                     │
│  语速:     [──●────] 0 (正常)                         │
│  回答超时: [15] 秒                                    │
│  最大轮数: [10] 轮                                    │
│                                                       │
│  ☐ 播报例句                                           │
│  ☐ 翻译例句                                           │
│  ☑ 错误时提示近义词/反义词                            │
│                                                       │
│           [取消]  [保存]                              │
└──────────────────────────────────────────────────────┘
```

### 6.4 核心组件代码示例（Vue 3 Composition API）

`DictTaskManagement.vue` 关键片段：

```vue
<template>
  <div class="dict-task-management">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :inline="true" :model="queryParams">
        <el-form-item label="任务名">
          <el-input v-model="queryParams.taskName" placeholder="请输入任务名" clearable />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="handleAdd">+ 新建任务</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 任务列表 -->
    <el-card>
      <el-table :data="taskList" v-loading="loading" border>
        <el-table-column prop="taskName" label="任务名称" min-width="180" />
        <el-table-column label="来源" width="120">
          <template #default="{ row }">
            {{ row.bookId ? '词书' : '手动输入' }}
          </template>
        </el-table-column>
        <el-table-column label="听写模式" width="120">
          <template #default="{ row }">
            <el-tag :type="row.mode === 'listen_en_answer_cn' ? 'primary' : 'success'">
              {{ row.mode === 'listen_en_answer_cn' ? '听英答中' : '听中答英' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="拼写考核" width="120">
          <template #default="{ row }">
            <span v-if="row.mode === 'listen_cn_answer_en'">
              {{ spellingModeMap[row.spellingCheckMode] }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-switch v-model="row.status" :active-value="1" :inactive-value="0"
                       @change="handleStatusChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="queryParams.page" v-model:page-size="queryParams.limit"
                     :total="total" @current-change="loadList" layout="total, prev, pager, next" />
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="720px" @close="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="form.taskName" placeholder="请输入任务名称" />
        </el-form-item>

        <el-form-item label="词汇来源">
          <el-radio-group v-model="form.sourceType">
            <el-radio value="book">从词书选择</el-radio>
            <el-radio value="manual">手动输入</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 词书选择 -->
        <template v-if="form.sourceType === 'book'">
          <el-form-item label="词书" prop="bookId">
            <el-select v-model="form.bookId" placeholder="请选择词书" filterable
                       @change="loadBookWords" style="width: 400px">
              <el-option v-for="book in bookList" :key="book.id" :label="book.name" :value="book.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="选择单词">
            <el-checkbox-group v-model="form.selectedWordIds">
              <el-checkbox v-for="w in bookWords" :key="w.id" :value="w.id" :label="w.id">
                {{ w.word }} ({{ w.meaning }})
              </el-checkbox>
            </el-checkbox-group>
          </el-form-item>
        </template>

        <!-- 手动输入 -->
        <template v-else>
          <el-form-item label="单词列表" prop="wordsText">
            <el-input v-model="form.wordsText" type="textarea" :rows="6"
                      placeholder="每行一个：单词 - 中文释义&#10;如：apple - 苹果" />
          </el-form-item>
        </template>

        <el-divider content-position="left">听写配置</el-divider>

        <el-form-item label="听写模式" prop="mode">
          <el-radio-group v-model="form.mode">
            <el-radio value="listen_en_answer_cn">听英文答中文</el-radio>
            <el-radio value="listen_cn_answer_en">听中文答英文</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="口音">
          <el-radio-group v-model="form.accent">
            <el-radio value="us">美式</el-radio>
            <el-radio value="uk">英式</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 拼写考核方式（仅听中文答英文时显示） -->
        <el-form-item v-if="form.mode === 'listen_cn_answer_en'" label="拼写考核方式">
          <el-radio-group v-model="form.spellingCheckMode">
            <el-radio value="spelling_only">仅字母拼读 (b-i-g)</el-radio>
            <el-radio value="word_only">仅整词读法 (big)</el-radio>
            <el-radio value="both">两者都要 (先 b-i-g，再 big)</el-radio>
          </el-radio-group>
          <div class="tip-text">
            <el-text type="info" size="small">
              * 仅字母拼读：用户逐字母拼出单词即可通过
              * 仅整词读法：用户直接读出整个单词即可通过
              * 两者都要：用户需先字母拼读通过，再整词读法通过
            </el-text>
          </div>
        </el-form-item>

        <el-form-item label="单词间隔">
          <el-slider v-model="form.intervalSeconds" :min="3" :max="15" :step="0.5"
                     show-input style="width: 400px" />
          <span style="margin-left: 8px">秒</span>
        </el-form-item>

        <el-form-item label="播报次数">
          <el-input-number v-model="form.repeatCount" :min="1" :max="3" />
        </el-form-item>

        <el-form-item label="语速">
          <el-slider v-model="form.speakRate" :min="-50" :max="50" :step="5"
                     show-input style="width: 400px" />
        </el-form-item>

        <el-form-item label="回答超时">
          <el-input-number v-model="form.answerTimeout" :min="5" :max="60" />
          <span style="margin-left: 8px">秒</span>
        </el-form-item>

        <el-form-item label="最大轮数">
          <el-input-number v-model="form.maxRounds" :min="1" :max="20" />
        </el-form-item>

        <el-divider content-position="left">反馈配置</el-divider>

        <el-form-item label="播报例句">
          <el-switch v-model="form.showExample" />
        </el-form-item>
        <el-form-item v-if="form.showExample" label="翻译例句">
          <el-switch v-model="form.exampleTranslate" />
        </el-form-item>
        <el-form-item label="错误提示近/反义词">
          <el-switch v-model="form.showSynonym" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getDictTaskPage, saveDictTask, deleteDictTask,
         getVocabularyBooks, getBookWords } from '@/api/dict'

// 拼写考核方式映射
const spellingModeMap = {
  spelling_only: '仅拼读',
  word_only: '仅整词',
  both: '两者都要'
}

// 查询参数
const queryParams = reactive({
  taskName: '', status: '', page: 1, limit: 10
})
const total = ref(0)
const taskList = ref([])
const loading = ref(false)

// 弹窗
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()
const bookList = ref([])
const bookWords = ref([])

// 表单数据
const form = reactive({
  id: '',
  taskName: '',
  sourceType: 'book',
  bookId: '',
  selectedWordIds: [],
  wordsText: '',
  mode: 'listen_en_answer_cn',
  accent: 'us',
  spellingCheckMode: 'spelling_only',
  intervalSeconds: 5.0,
  repeatCount: 1,
  speakRate: 0,
  answerTimeout: 15,
  maxRounds: 10,
  showExample: false,
  exampleTranslate: false,
  showSynonym: true,
})

const rules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  mode: [{ required: true, message: '请选择听写模式', trigger: 'change' }],
}

// 加载列表
const loadList = async () => {
  loading.value = true
  try {
    const res = await getDictTaskPage(queryParams)
    taskList.value = res.data.list
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

// 加载词书
const loadBooks = async () => {
  const res = await getVocabularyBooks()
  bookList.value = res.data
}

// 加载词书下的单词
const loadBookWords = async (bookId) => {
  const res = await getBookWords(bookId)
  bookWords.value = res.data
}

// 新建
const handleAdd = () => {
  dialogTitle.value = '新建听写任务'
  Object.assign(form, {
    id: '', taskName: '', sourceType: 'book', bookId: '',
    selectedWordIds: [], wordsText: '', mode: 'listen_en_answer_cn',
    accent: 'us', spellingCheckMode: 'spelling_only', intervalSeconds: 5.0,
    repeatCount: 1, speakRate: 0, answerTimeout: 15, maxRounds: 10,
    showExample: false, exampleTranslate: false, showSynonym: true,
  })
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row) => {
  dialogTitle.value = '编辑听写任务'
  Object.assign(form, row)
  if (row.bookId) {
    form.sourceType = 'book'
    loadBookWords(row.bookId)
  } else {
    form.sourceType = 'manual'
  }
  dialogVisible.value = true
}

// 保存
const handleSubmit = async () => {
  await formRef.value.validate()
  await saveDictTask(form)
  ElMessage.success('保存成功')
  dialogVisible.value = false
  loadList()
}

// 删除
const handleDelete = async (row) => {
  await ElMessageBox.confirm('确认删除该听写任务？', '提示', { type: 'warning' })
  await deleteDictTask(row.id)
  ElMessage.success('删除成功')
  loadList()
}

onMounted(() => {
  loadList()
  loadBooks()
})
</script>

<style scoped>
.dict-task-management { padding: 20px; }
.search-card { margin-bottom: 16px; }
.tip-text { margin-top: 4px; line-height: 1.6; }
</style>
```

### 6.5 API 封装 `api/dict.js`

```javascript
import request from '@/utils/request'

// 词汇相关
export const getVocabularyBooks = () => request.get('/api/dict/books/list')
export const getBookWords = (bookId, params) =>
  request.get(`/api/dict/books/${bookId}/words`, { params })

// 听写任务
export const getDictTaskPage = (params) => request.get('/api/dict/task/page', { params })
export const getDictTask = (id) => request.get(`/api/dict/task/${id}`)
export const saveDictTask = (data) =>
  data.id ? request.put('/api/dict/task', data) : request.post('/api/dict/task', data)
export const deleteDictTask = (id) => request.delete(`/api/dict/task/${id}`)

// 听写记录
export const getDictRecordPage = (params) => request.get('/api/dict/record/page', { params })
export const getDictRecord = (id) => request.get(`/api/dict/record/${id}`)
```

### 6.6 听写记录页 `DictRecordManagement.vue`

```
┌──────────────────────────────────────────────────────┐
│  听写记录                                              │
├──────────────────────────────────────────────────────┤
│  时间范围: [2026-07-01] ~ [2026-07-19] [查询]         │
├──────────────────────────────────────────────────────┤
│  任务名        │ 时间       │ 正确率 │ 轮数 │ 详情    │
│  三年级Unit1  │ 07-19 18:30│ 85%   │ 2   │ 查看    │
└──────────────────────────────────────────────────────┘
```

**详情弹窗**：展示每轮每词的对错详情、用户回答记录。

## 七、关键技术细节

### 7.1 复用连接默认的 TTS 管道

**方案**：听写过程中的所有播报都通过 `conn.tts` 默认管道发送，不直接调用 edge-tts。

**实现要点**：
- 通过 `TTSMessageDTO`（FIRST → MIDDLE(TEXT) → LAST）三段式发送文本到 `conn.tts.tts_text_queue`
- 语音、语速、音量等参数使用 Agent 级别配置（在后台 Agent 管理中配置好 edge-tts 语音）
- 推荐为听写场景配置一个英文语音的 Agent（如 `en-US-JennyNeural` 美式 / `en-GB-SoniaNeural` 英式），edge-tts 的英文语音也能读中文（效果可接受，用于中文反馈语）
- 若需要更精确的中英文语音切换，可在 Agent 配置中选择支持中英双语的语音（如 `zh-CN-XiaoxiaoNeural` 读中文清晰，读英文也可用）

**语音配置建议**（在后台 Agent 管理 → TTS 配置中设置）：
- 美式口音场景：`voice = en-US-JennyNeural`
- 英式口音场景：`voice = en-GB-SoniaNeural`

### 7.2 答案判定机制

#### 听英文答中文（LLM 判定）

用户听到英文单词后，用中文说出含义。由于中文表达方式多样（同义词、近义词、部分关键词），使用 LLM 判定：

```python
prompt = f"""判断用户的中文回答是否表达了单词的正确含义。
单词：{word.word}
标准释义：{word.meaning}
用户回答：{user_answer}
只回答JSON：{{"correct": true}} 或 {{"correct": false}}"""
```

- LLM 调用走 `conn.llm.response_text_with_prompt(prompt)`
- 超时 10 秒，超时或异常时 fallback 到简单字符串包含匹配

#### 听中文答英文（三种可配置的拼写考核方式）

用户听到中文释义后，用英文回答。支持三种可配置的考核方式（由 `spellingCheckMode` 字段控制）：

**模式 1：`spelling_only`（仅字母拼读）**

用户只需逐字母拼出单词即可通过。判定流程：
1. **规则匹配**（快速路径）：去掉分隔符后直接比较（"b-i-g" → "big"）
2. **LLM 判定**（兜底路径）：规则未命中时，调用 LLM 判断用户回答是否为正确字母拼读，可处理 ASR 将字母识别为谐音词的情况（如 b→bee, c→see, i→eye）
- 示例：题目 `big`，回答 "b-i-g" → 规则匹配 ✓；回答 "bee eye gee" → LLM 判定 ✓；回答 "big" → ✗（整词不算）

**模式 2：`word_only`（仅整词读法）**

用户只需直接读出整个单词即可通过。
- 精确匹配 + 容错：忽略空格、编辑距离 ≤ 1（轻微发音误差）
- 示例：题目 `big`，回答 "big" → 匹配 ✓；回答 "b-i-g" → ✗（字母拼读不算）

**模式 3：`both`（两者都要）**

用户需先字母拼读通过，再整词读法通过，两阶段都正确才算通过。
- 阶段 1（spelling）：播报"请先用字母拼读"，等待用户回答 "b-i-g"
  - 通过 → 播报"拼读正确！现在请读出整个单词"，进入阶段 2
  - 失败 → 整词判错，播报错误反馈
- 阶段 2（word）：等待用户回答 "big"
  - 通过 → 整词判对，播报夸奖语
  - 失败 → 整词判错，播报错误反馈
- 阶段状态记录在 `DictationWord.spelling_passed` 和 `word_passed` 字段

#### 错误反馈内容

答错时播报：
1. **鼓励语** + **正确答案**：
   - 听英文答中文：`正确答案是：苹果。英文是 apple。`
   - 听中文答英文：`正确的单词是：apple。字母拼写是：a-p-p-l-e。中文意思是：苹果。`
2. **例句**（如配置了播报例句且词汇表有例句）：`例句：I like to eat apples. 我喜欢吃苹果。`
3. **近义词/反义词**（如配置了）：`近义词有：fruit。`

### 7.3 状态机生命周期

听写会话绑定在 `ConnectionHandler` 上，是连接级状态：

| 场景 | 处理 |
|------|------|
| 设备断连 | `conn.close()` 清理时，`dictation_session = None`，已有记录不保存（或可选择保存） |
| 回答超时 | 自动判错，记录错误，进入下一词 |
| 用户主动退出 | 识别"停止听写"等关键词，保存记录，清理状态 |
| 全部通过 | 播报结束语，保存记录，清理状态 |
| 达到最大轮数 | 播报结束，保存记录（含未通过词），清理状态 |

### 7.4 配置下发流程

```
后台配置听写任务 → 存入 dict_task 表
                                    ↓
设备语音"开始听写" → ASR → function_call: start_dictation
                                    ↓
Python 端调用 /api/dict/task/active → Java 返回完整任务配置（含单词列表）
                                    ↓
创建 DictationSession → 开始听写循环
                                    ↓
听写结束 → Python 端调用 POST /api/dict/record → Java 保存记录
```

## 八、实现优先级

| 优先级 | 模块 | 说明 |
|--------|------|------|
| P0 | 数据库表（dict_task / dict_record） | Liquibase 变更集，含 spelling_check_mode 字段 |
| P0 | Java 后台 API（任务管理 + 词汇查询 + 记录保存） | 含 content JSON 解析、spellingCheckMode 字段透传 |
| P0 | Python 听写会话状态机 + 核心处理器 | `dictationHandler.py`，含 both 模式两阶段判定 |
| P0 | 插件注册 + 接入 ConnectionHandler | `dictation.py` 插件 + textHandle 改造 |
| P0 | 答案判定（LLM 判中文 + 三模式判英文） | `_check_spelling`（规则+LLM）/ `_check_whole_word`（编辑距离） |
| P0 | Vue 3 + Element Plus 前端框架搭建 | 项目升级到 Vue 3 + Element Plus |
| P1 | Vue 听写任务管理页 | 含词书单词选择 + 手动输入 + 拼写考核方式配置 |
| P1 | Vue 听写记录页 | 列表 + 详情 |
| P2 | 错题本（自动收集错误词生成专项任务） | 基于现有数据扩展 |
| P2 | 听写统计面板（正确率趋势图） | 数据可视化 |

## 九、扩展能力（未来可选）

- **错题本**：自动收集所有听写错误的词，一键生成"错题专项复习"任务
- **家长通知**：听写完成后通过微信/钉钉机器人推送结果
- **跟读训练**：在听写基础上增加"跟读打分"功能，对比用户发音与标准发音
- **智能推荐**：根据历史听写正确率，自动推荐需要复习的单词生成任务
- **语音语调训练**：扩展支持句子听写、段落跟读等

## 十、附录

### 10.1 相关源码位置

| 文件 | 说明 |
|------|------|
| `xiaozhi-server/core/connection.py` | ConnectionHandler，需新增 dictation_session 字段 |
| `xiaozhi-server/core/handle/textHandle.py` | 文本消息路由，需插入听写会话判断 |
| `xiaozhi-server/core/handle/intentHandler.py` | 意图处理，function_call 模式下走工具调用 |
| `xiaozhi-server/core/providers/tts/edge.py` | edge-tts 实现，听写复用此管道 |
| `xiaozhi-server/plugins_func/register.py` | 插件注册装饰器、Action/ActionResponse 定义 |
| `xiaozhi-server/plugins_func/functions/get_time.py` | 插件示例参考 |
| `manager-api/src/main/resources/db/changelog/` | Liquibase 变更集目录 |
| `manager-api/src/main/resources/db/changelog/biz_vocabularies.sql` | 词汇数据（已有） |
| `manager-api/src/main/resources/db/changelog/biz_vocabulary_books.sql` | 词书数据（已有） |
| `manager-web/src/router/index.js` | 前端路由配置 |

### 10.2 edge-tts 推荐语音列表

| 口音 | 语音名称 | 性别 |
|------|----------|------|
| 美式 | `en-US-JennyNeural` | 女 |
| 美式 | `en-US-GuyNeural` | 男 |
| 英式 | `en-GB-SoniaNeural` | 女 |
| 英式 | `en-GB-RyanNeural` | 男 |
| 中文 | `zh-CN-XiaoxiaoNeural` | 女 |

### 10.3 字母拼读判定策略说明

字母拼读判定（`spelling_only` 和 `both` 模式的 spelling 阶段）采用 **规则匹配 + LLM 兜底** 两级策略，无需维护字母谐音映射表：

1. **规则匹配（快速路径，零延迟）**：去掉分隔符后直接比较
   - "b-i-g" / "b i g" / "b,i,g" → 去分隔符得 "big" → 与正确单词字母拼接比较
2. **LLM 判定（兜底路径，处理 ASR 谐音误识别）**：规则未命中时调用 LLM
   - LLM 能理解 ASR 将字母识别为谐音词的情况（b→bee, c→see, i→eye 等）
   - 超时或异常时 fallback 到规则匹配

**优势**：无需硬编码维护字母谐音映射表，LLM 覆盖更全面；规则匹配保证常见情况零延迟。
