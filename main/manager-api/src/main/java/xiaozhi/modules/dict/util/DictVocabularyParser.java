package xiaozhi.modules.dict.util;

import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import xiaozhi.modules.dict.entity.BizVocabularyEntity;
import xiaozhi.modules.dict.vo.DictVocabularyVO;

/**
 * 词汇 content JSON 解析工具
 * <p>
 * biz_vocabularies.content 字段是嵌套 JSON，结构示例：
 * <pre>
 * {
 *   "word": {
 *     "content": {
 *       "trans": [{"pos": "n", "tranCn": "药房", "tranOther": "a shop..."}],
 *       "syno": {"synos": [{"pos": "n", "hwds": [{"w": "dispensary"}], "tran": "药房"}]},
 *       "antos": {"anto": [{"hwd": "inept"}]},
 *       "sentence": {"sentences": [{"scontent": "...", "scn": "..."}]},
 *       "remMethod": {"val": "pharma(药) + cy → 药店"}
 *     }
 *   }
 * }
 * </pre>
 */
public final class DictVocabularyParser {

    private DictVocabularyParser() {
    }

    /**
     * 将 BizVocabularyEntity 解析为 DictVocabularyVO
     */
    public static DictVocabularyVO parse(BizVocabularyEntity entity) {
        if (entity == null) {
            return null;
        }
        DictVocabularyVO vo = new DictVocabularyVO();
        vo.setId(entity.getId());
        vo.setWord(entity.getWord());
        vo.setPhoneticUs(entity.getUsphone());
        vo.setPhoneticUk(entity.getUkphone());
        parseContent(entity.getContent(), vo);
        return vo;
    }

    /**
     * 解析 content JSON 字符串，填充到 vo 中
     */
    public static void parseContent(String contentJson, DictVocabularyVO vo) {
        if (StrUtil.isBlank(contentJson)) {
            return;
        }
        try {
            JSONObject root = JSONUtil.parseObj(contentJson);
            JSONObject wordObj = root.getJSONObject("word");
            if (wordObj == null) {
                return;
            }
            JSONObject content = wordObj.getJSONObject("content");
            if (content == null) {
                return;
            }

            // 中文释义 + 英文释义
            JSONArray trans = content.getJSONArray("trans");
            if (trans != null && !trans.isEmpty()) {
                JSONObject firstTrans = trans.getJSONObject(0);
                if (firstTrans != null) {
                    vo.setMeaning(firstTrans.getStr("tranCn"));
                    vo.setMeaningEn(firstTrans.getStr("tranOther"));
                }
            }

            // 例句
            JSONObject sentenceObj = content.getJSONObject("sentence");
            if (sentenceObj != null) {
                JSONArray sentences = sentenceObj.getJSONArray("sentences");
                if (sentences != null && !sentences.isEmpty()) {
                    JSONObject firstSentence = sentences.getJSONObject(0);
                    if (firstSentence != null) {
                        vo.setExampleSentence(firstSentence.getStr("scontent"));
                        vo.setExampleTranslation(firstSentence.getStr("scn"));
                    }
                }
            }

            // 近义词
            JSONObject syno = content.getJSONObject("syno");
            if (syno != null) {
                JSONArray synos = syno.getJSONArray("synos");
                if (synos != null) {
                    List<String> synonyms = new ArrayList<>();
                    for (int i = 0; i < synos.size(); i++) {
                        JSONObject synoItem = synos.getJSONObject(i);
                        if (synoItem == null) {
                            continue;
                        }
                        JSONArray hwds = synoItem.getJSONArray("hwds");
                        if (hwds == null) {
                            continue;
                        }
                        for (int j = 0; j < hwds.size(); j++) {
                            JSONObject hwd = hwds.getJSONObject(j);
                            if (hwd != null && hwd.getStr("w") != null) {
                                synonyms.add(hwd.getStr("w"));
                            }
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
                        JSONObject antoItem = anto.getJSONObject(i);
                        if (antoItem != null && antoItem.getStr("hwd") != null) {
                            antonyms.add(antoItem.getStr("hwd"));
                        }
                    }
                    vo.setAntonyms(antonyms);
                }
            }

            // 记忆方法
            JSONObject remMethod = content.getJSONObject("remMethod");
            if (remMethod != null) {
                vo.setRemMethod(remMethod.getStr("val"));
            }
        } catch (Exception e) {
            // 解析失败不抛异常，仅保留 word/phonetic 等基础字段
        }
    }
}
