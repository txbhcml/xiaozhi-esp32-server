package xiaozhi.modules.dict.service;

import java.util.List;

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
     * 词书内单词总数
     */
    long countWordsByBook(Long bookId, String word);

    /**
     * 按 ID 批量查询单词（解析 content）
     */
    List<DictVocabularyVO> listWordsByIds(List<Long> ids);
}
