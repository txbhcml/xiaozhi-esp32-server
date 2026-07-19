-- 声纹服务供应商：将声纹服务配置从 sys_params 迁移到 ai_model_config 统一管理
-- 支持多种声纹 provider（讯飞、阿里云等），参考 ASR provider 模式
INSERT IGNORE INTO `ai_model_provider` (`id`, `model_type`, `provider_code`, `name`, `fields`, `sort`, `creator`, `create_date`, `updater`, `update_date`) VALUES
('SYSTEM_Voiceprint_xunfei', 'Voiceprint', 'xunfei', '讯飞声纹识别',
 '[{"key":"type","label":"声纹类型","type":"string"},{"key":"url","label":"声纹服务地址","type":"string"},{"key":"key","label":"鉴权密钥","type":"password"},{"key":"app_id","label":"应用ID","type":"string"},{"key":"api_key","label":"API密钥","type":"password"},{"key":"api_secret","label":"API密钥Secret","type":"password"},{"key":"group_id","label":"声纹组ID","type":"string"},{"key":"similarity_threshold","label":"相似度阈值","type":"number"}]',
 1, 1, NOW(), 1, NOW());
