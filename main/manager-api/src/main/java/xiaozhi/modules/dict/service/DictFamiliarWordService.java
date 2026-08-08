package xiaozhi.modules.dict.service;

import java.util.Map;
import java.util.Set;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.dict.vo.DictVocabularyVO;

/**
 * 标熟单词 Service
 */
public interface DictFamiliarWordService {

    /**
     * 标熟
     */
    void markFamiliar(Long vocabId, String word, Long bookId);

    /**
     * 取消标熟（按标熟记录ID删除）
     */
    void unmarkFamiliar(Long familiarId);

    /**
     * 获取用户某词书的标熟单词ID集合
     */
    Set<Long> getFamiliarVocabIds(Long userId, Long bookId);

    /**
     * 获取用户所有已标熟单词的小写集合（跨词书）
     */
    Set<String> getFamiliarWords(Long userId);

    /**
     * 获取用户某词书的标熟记录映射（vocabId → familiarId）
     */
    Map<Long, Long> getFamiliarVocabIdMap(Long userId, Long bookId);

    /**
     * 分页查询词书单词（含标熟状态）
     */
    PageData<DictVocabularyVO> pageWordsWithFamiliar(Long bookId, String word, Integer page, Integer limit);

    /**
     * 分页查询当前用户所有标熟单词（跨词书）
     *
     * @param word 单词模糊匹配（可空，同时匹配 word 和冗余的 word 字段）
     */
    PageData<DictVocabularyVO> pageFamiliarWords(String word, Integer page, Integer limit);
}
