# 声纹服务合并到 xiaozhi-server + 配置迁移到 ai_model_config

## Context

当前声纹服务架构有两个问题：
1. **配置分散**：声纹服务地址存储在 `sys_params.server.voice_print`，智控台页面没有配置入口
2. **独立服务**：voiceprint-xunfei 是独立 Python 服务，需单独部署和维护

用户希望：
1. 在智控台模型配置页面（`/#/model-config`）统一管理声纹配置
2. 不搞独立服务，将声纹服务合并到 xiaozhi-server

## 目标架构

```
智控台 → ai_model_config(Voiceprint) → manager-api
    ↓ /config/server-base 返回 voiceprint 配置（含讯飞凭据）
xiaozhi-server
    ├─ 声纹识别：VoiceprintProvider 直接调用讯飞 API（在 ASR 流程内部）
    └─ 声纹注册/删除：8003 端口 HTTP 接口（供 manager-api 调用）

manager-api AgentVoicePrintServiceImpl
    └─ 调用 xiaozhi-server:8003/voiceprint/register（声纹注册/删除）
```

**关键变化**：
- 废弃 voiceprint-xunfei 独立服务
- 讯飞声纹 API 调用逻辑移到 xiaozhi-server
- 声纹识别不再通过 HTTP 调用外部服务，直接在 xiaozhi-server 内部调用讯飞 API
- 声纹注册/删除通过 xiaozhi-server 的 8003 端口 HTTP 接口

## 实现步骤

### 第一部分：xiaozhi-server 新增讯飞声纹模块

#### 1. 新建声纹 provider 基类和讯飞实现

**新建文件**：`main/xiaozhi-server/core/providers/voiceprint/base.py`
- 定义 `VoiceprintProviderBase` 抽象基类
- 抽象方法：`register_voiceprint(speaker_id, audio_bytes)`, `identify_voiceprint(audio_bytes, speaker_ids)`, `delete_voiceprint(speaker_id)`, `check_health()`

**新建文件**：`main/xiaozhi-server/core/providers/voiceprint/xunfei.py`
- 将 voiceprint-xunfei/app.py 中的讯飞 API 调用逻辑迁移过来
- 实现 `XfyunVoiceprintProvider` 类
- 包含鉴权（HMAC-SHA256 签名）、注册、识别、删除方法
- 使用 httpx 异步调用讯飞 API

**新建文件**：`main/xiaozhi-server/core/providers/voiceprint/__init__.py`
- 工厂方法 `create_instance(class_name, config)`，参考 `core/utils/asr.py` 的动态加载模式

#### 2. 修改 VoiceprintProvider

**修改文件**：[voiceprint_provider.py](file:///Users/chenmenglong/IdeaProjects/xiaozhi-esp32-server/main/xiaozhi-server/core/utils/voiceprint_provider.py)

当前 `identify_speaker()` 通过 HTTP 调用外部声纹服务。改为：
- `__init__()` 从配置读取讯飞凭据（app_id, api_key, api_secret, group_id, type）
- 创建对应的声纹 provider 实例（如 `XfyunVoiceprintProvider`）
- `identify_speaker()` 直接调用 provider 的 `identify_voiceprint()` 方法（不再 HTTP 调用）
- `_check_server_health()` 改为调用 provider 的 `check_health()` 方法

**配置结构变化**：
```python
# 当前配置
config = {"url": "http://host:8005/voiceprint/health?key=token", "speakers": [...], "similarity_threshold": 0.4}

# 新配置
config = {
    "type": "xunfei",
    "app_id": "...", "api_key": "...", "api_secret": "...", "group_id": "...",
    "speakers": [...], "similarity_threshold": 0.4
}
```

#### 3. 新增声纹 HTTP 接口

**新建文件**：`main/xiaozhi-server/core/api/voiceprint_handler.py`
- 参考 `core/api/vision_handler.py` 的模式
- 处理类 `VoiceprintHandler`，使用全局的 VoiceprintProvider 实例
- 接口：
  - `GET /voiceprint/health` - 健康检查
  - `POST /voiceprint/register` - 注册声纹（multipart/form-data：speaker_id + file）
  - `DELETE /voiceprint/{speaker_id}` - 删除声纹
  - `OPTIONS /voiceprint/*` - CORS 预检

**修改文件**：[http_server.py](file:///Users/chenmenglong/IdeaProjects/xiaozhi-esp32-server/main/xiaozhi-server/core/http_server.py#L66-L76)
- 在 `app.add_routes()` 中注册声纹路由
- VoiceprintProvider 实例通过 config 传递给 handler

### 第二部分：manager-api 配置迁移

#### 4. 数据库新增 voiceprint 供应商

**新建文件**：`main/manager-api/src/main/resources/db/changelog/202607131200.sql`

```sql
INSERT IGNORE INTO ai_model_provider (id, model_type, provider_code, name, fields, sort, creator, create_date, updater, update_date)
VALUES ('SYSTEM_Voiceprint_xunfei', 'Voiceprint', 'xunfei', '讯飞声纹识别',
 '[{"key":"url","label":"声纹服务地址","type":"string"},{"key":"key","label":"鉴权密钥","type":"password"},{"key":"app_id","label":"应用ID","type":"string"},{"key":"api_key","label":"API密钥","type":"password"},{"key":"api_secret","label":"API密钥Secret","type":"password"},{"key":"group_id","label":"声纹组ID","type":"string"},{"key":"similarity_threshold","label":"相似度阈值","type":"number"}]',
 1, 1, NOW(), 1, NOW());
```

**修改文件**：`main/manager-api/src/main/resources/db/changelog/db.changelog-master.yaml` - 追加 changeSet

#### 5. 后端补充敏感字段掩码

**修改文件**：[SensitiveDataUtils.java](file:///Users/chenmenglong/IdeaProjects/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/common/utils/SensitiveDataUtils.java#L19-L21)
- SENSITIVE_FIELDS 集合添加 `"key"` 和 `"api_secret"`

#### 6. 防止 VOICEPRINT 类型导致非法 SQL

**修改文件**：[AgentTemplateServiceImpl.java](file:///Users/chenmenglong/IdeaProjects/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentTemplateServiceImpl.java#L46-L51)
- `updateDefaultTemplateModelId` 方法新增 VOICEPRINT early return

#### 7. ConfigServiceImpl 改为从 ai_model_config 读取

**修改文件**：[ConfigServiceImpl.java](file:///Users/chenmenglong/IdeaProjects/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/config/service/impl/ConfigServiceImpl.java#L339-L387)

修改 `buildVoiceprintConfig` 方法：
1. 从 `modelConfigService.getEnabledModelsByType("Voiceprint")` 读取配置
2. 从 config_json 提取讯飞凭据（app_id, api_key, api_secret, group_id, similarity_threshold）
3. 构建新的 voiceprint 配置结构（不再拼接 url?key=xxx，而是直接传递凭据）
4. 回退逻辑：ai_model_config 无记录时回退到 sys_params（向后兼容）

**返回给 xiaozhi-server 的配置结构**：
```json
{
  "type": "xunfei",
  "app_id": "...", "api_key": "...", "api_secret": "...", "group_id": "...",
  "similarity_threshold": 0.4,
  "speakers": ["id,name,description", ...]
}
```

#### 8. AgentVoicePrintServiceImpl 改为调用 xiaozhi-server

**修改文件**：[AgentVoicePrintServiceImpl.java](file:///Users/chenmenglong/IdeaProjects/xiaozhi-esp32-server/main/manager-api/src/main/java/xiaozhi/modules/agent/service/impl/AgentVoicePrintServiceImpl.java)

- 新增 `ModelConfigService` 依赖注入
- `getVoicePrintURI()` 从 ai_model_config 读取 url 和 key（url 指向 xiaozhi-server:8003/voiceprint）
- 回退逻辑：无 ai_model_config 记录时回退到 sys_params
- 下游 `getBaseUrl()`、`getAuthorization()` 无需改动

### 第三部分：前端

#### 9. 新增声纹菜单项

**修改文件**：[ModelConfig.vue](file:///Users/chenmenglong/IdeaProjects/xiaozhi-esp32-server/main/manager-web/src/views/ModelConfig.vue#L55-L57)
- 在 `rag` 菜单项后添加 voiceprint 菜单项

#### 10. i18n 翻译

6 个 i18n 文件添加 `modelConfig.voiceprint` key：
- zh_CN: `'声纹服务'`
- zh_TW: `'聲紋服務'`
- en: `'Voiceprint'`
- vi, pt_BR, de 对应翻译

### 第四部分：清理

#### 11. 废弃 voiceprint-xunfei

- 删除 `main/voiceprint-xunfei/` 目录
- 相关逻辑已迁移到 xiaozhi-server

## 向后兼容性

- `sys_params.server.voice_print` 保留，两处读取点均先查 ai_model_config，无记录则回退 sys_params
- 旧格式的 voiceprint 配置（url?key=xxx）仍可工作（VoiceprintProvider 兼容旧格式）
- 用户未配置 ai_model_config 时，系统继续使用 sys_params

## 验证方法

1. **数据库**：liquibase 执行新 changelog，`ai_model_provider` 有 Voiceprint 记录
2. **前端**：模型配置页面出现"声纹服务"菜单项，可新增讯飞声纹配置（7 个字段）
3. **后端 API**：`GET /models/voiceprint/provideTypes` 返回讯飞供应商；敏感字段被掩码
4. **配置下发**：`POST /config/server-base` 返回的 voiceprint 配置包含讯飞凭据
5. **声纹识别**：xiaozhi-server 日志显示 `声纹识别已启用`，对话时调用讯飞 API 识别说话人
6. **声纹注册**：智控台注册声纹，manager-api 调用 xiaozhi-server:8003/voiceprint/register，讯飞 API 注册成功
7. **声纹删除**：智控台删除声纹，manager-api 调用 xiaozhi-server:8003/voiceprint/{id}，讯飞 API 删除成功
8. **向后兼容**：删除 ai_model_config 中的声纹配置，确认回退到 sys_params 正常

## 注意事项

- voiceprint-xunfei 的讯飞 API 调用逻辑（鉴权、注册、识别、删除）迁移到 `core/providers/voiceprint/xunfei.py`
- xiaozhi-server 的声纹 HTTP 接口需要鉴权（使用 config_json 中的 key）
- manager-api 调用 xiaozhi-server 声纹接口时，url 从 ai_model_config 的 config_json.url 读取
- 声纹注册/删除接口需要处理音频文件上传（multipart/form-data）
