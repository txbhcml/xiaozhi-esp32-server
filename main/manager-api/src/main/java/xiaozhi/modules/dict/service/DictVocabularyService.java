package xiaozhi.modules.dict.service;

import java.util.List;
import java.util.Set;

import xiaozhi.modules.dict.dto.BatchImportDTO;
import xiaozhi.modules.dict.vo.BizVocabularyBookVO;
import xiaozhi.modules.dict.vo.DictVocabularyVO;

/**
 * 词汇 Service（只读：词书列表、词书内分页查词、按 ID 批量查词）
 */
public interface DictVocabularyService {

    /**
     * 列出所有词书
     */
    List<BizVocabularyBookVO> listBooks();

    /**
     * 分页查询词书内单词
     *
     * @param bookId 词书ID
     * @param word   单词模糊匹配（可空）
     * @param page   页码（1 开始）
     * @param limit  每页数量
     */
    List<DictVocabularyVO> listWordsByBook(Long bookId, String word, Integer page, Integer limit);

    /**
     * 分页查询词书内单词（排除指定ID集合，用于过滤已标熟单词）
     *
     * @param excludeIds 需要排除的词汇ID集合（可空）
     */
    List<DictVocabularyVO> listWordsByBook(Long bookId, String word, Integer page, Integer limit, Set<Long> excludeIds);

    /**
     * 词书内单词总数
     */
    long countWordsByBook(Long bookId, String word);

    /**
     * 词书内单词总数（排除指定ID集合）
     *
     * @param excludeIds 需要排除的词汇ID集合（可空）
     */
    long countWordsByBook(Long bookId, String word, Set<Long> excludeIds);

    /**
     * 按 ID 批量查询单词（解析 content）
     */
    List<DictVocabularyVO> listWordsByIds(List<Long> ids);

    /**
     * 批量导入单词（词书查找 + LLM 翻译）
     *
     * @param dto 单词列表 + 可选词书ID
     * @return 单词及其释义列表（只含 word 和 meaning）
     */
    List<DictVocabularyVO> batchImport(BatchImportDTO dto);
}
