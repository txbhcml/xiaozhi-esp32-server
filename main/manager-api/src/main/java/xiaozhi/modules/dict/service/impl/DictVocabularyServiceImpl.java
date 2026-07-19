package xiaozhi.modules.dict.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import lombok.AllArgsConstructor;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.dict.dao.BizVocabularyBookDao;
import xiaozhi.modules.dict.dao.BizVocabularyDao;
import xiaozhi.modules.dict.entity.BizVocabularyBookEntity;
import xiaozhi.modules.dict.entity.BizVocabularyEntity;
import xiaozhi.modules.dict.service.DictVocabularyService;
import xiaozhi.modules.dict.util.DictVocabularyParser;
import xiaozhi.modules.dict.vo.BizVocabularyBookVO;
import xiaozhi.modules.dict.vo.DictVocabularyVO;

/**
 * 词汇 Service 实现
 */
@Service
@AllArgsConstructor
public class DictVocabularyServiceImpl extends BaseServiceImpl<BizVocabularyDao, BizVocabularyEntity>
        implements DictVocabularyService {

    private final BizVocabularyDao bizVocabularyDao;
    private final BizVocabularyBookDao bizVocabularyBookDao;

    @Override
    public List<BizVocabularyBookVO> listBooks() {
        LambdaQueryWrapper<BizVocabularyBookEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BizVocabularyBookEntity::getSortOrder)
                .orderByAsc(BizVocabularyBookEntity::getId);
        List<BizVocabularyBookEntity> entities = bizVocabularyBookDao.selectList(wrapper);
        List<BizVocabularyBookVO> result = new ArrayList<>();
        if (CollUtil.isEmpty(entities)) {
            return result;
        }
        for (BizVocabularyBookEntity e : entities) {
            BizVocabularyBookVO vo = new BizVocabularyBookVO();
            vo.setId(e.getId());
            vo.setName(e.getName());
            vo.setCode(e.getCode());
            vo.setDescription(e.getDescription());
            vo.setTotalWords(e.getTotalWords());
            vo.setSortOrder(e.getSortOrder());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<DictVocabularyVO> listWordsByBook(Long bookId, String word, Integer page, Integer limit) {
        if (bookId == null) {
            return new ArrayList<>();
        }
        long pageNo = page == null ? 1 : Math.max(1, page);
        long pageSize = limit == null ? 20 : Math.max(1, limit);
        Page<BizVocabularyEntity> p = new Page<>(pageNo, pageSize);

        LambdaQueryWrapper<BizVocabularyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizVocabularyEntity::getBookId, bookId)
                .orderByAsc(BizVocabularyEntity::getWordRank)
                .orderByAsc(BizVocabularyEntity::getId);
        if (StrUtil.isNotBlank(word)) {
            wrapper.like(BizVocabularyEntity::getWord, word);
        }
        bizVocabularyDao.selectPage(p, wrapper);
        return p.getRecords().stream()
                .map(DictVocabularyParser::parse)
                .collect(Collectors.toList());
    }

    @Override
    public long countWordsByBook(Long bookId, String word) {
        if (bookId == null) {
            return 0L;
        }
        LambdaQueryWrapper<BizVocabularyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizVocabularyEntity::getBookId, bookId);
        if (StrUtil.isNotBlank(word)) {
            wrapper.like(BizVocabularyEntity::getWord, word);
        }
        return bizVocabularyDao.selectCount(wrapper);
    }

    @Override
    public List<DictVocabularyVO> listWordsByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<BizVocabularyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizVocabularyEntity::getId, ids);
        List<BizVocabularyEntity> entities = bizVocabularyDao.selectList(wrapper);
        Map<Long, BizVocabularyEntity> idMap = entities.stream()
                .collect(Collectors.toMap(BizVocabularyEntity::getId, e -> e, (a, b) -> a));
        List<DictVocabularyVO> result = new ArrayList<>();
        for (Long id : ids) {
            BizVocabularyEntity vocab = idMap.get(id);
            if (vocab != null) {
                result.add(DictVocabularyParser.parse(vocab));
            }
        }
        return result;
    }
}
