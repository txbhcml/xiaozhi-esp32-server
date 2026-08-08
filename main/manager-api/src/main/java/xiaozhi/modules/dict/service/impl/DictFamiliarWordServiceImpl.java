package xiaozhi.modules.dict.service.impl;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.dict.dao.BizVocabularyBookDao;
import xiaozhi.modules.dict.dao.BizVocabularyDao;
import xiaozhi.modules.dict.dao.DictFamiliarWordDao;
import xiaozhi.modules.dict.entity.BizVocabularyBookEntity;
import xiaozhi.modules.dict.entity.BizVocabularyEntity;
import xiaozhi.modules.dict.entity.DictFamiliarWordEntity;
import xiaozhi.modules.dict.service.DictFamiliarWordService;
import xiaozhi.modules.dict.service.DictVocabularyService;
import xiaozhi.modules.dict.util.DictVocabularyParser;
import xiaozhi.modules.dict.vo.DictVocabularyVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 标熟单词 Service 实现
 */
@Service
@AllArgsConstructor
public class DictFamiliarWordServiceImpl extends BaseServiceImpl<DictFamiliarWordDao, DictFamiliarWordEntity>
        implements DictFamiliarWordService {

    private final DictFamiliarWordDao dictFamiliarWordDao;
    private final DictVocabularyService dictVocabularyService;
    private final BizVocabularyDao bizVocabularyDao;
    private final BizVocabularyBookDao bizVocabularyBookDao;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markFamiliar(Long vocabId, String word, Long bookId) {
        Long userId = SecurityUser.getUserId();
        if (userId == null || vocabId == null) {
            throw new RuntimeException("用户未登录或词汇ID为空");
        }
        // 已存在则跳过（唯一键 uk_user_vocab 兜底）
        LambdaQueryWrapper<DictFamiliarWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictFamiliarWordEntity::getUserId, userId)
                .eq(DictFamiliarWordEntity::getVocabId, vocabId);
        Long count = dictFamiliarWordDao.selectCount(wrapper);
        if (count != null && count > 0) {
            return;
        }
        DictFamiliarWordEntity entity = new DictFamiliarWordEntity();
        entity.setUserId(userId);
        entity.setVocabId(vocabId);
        entity.setWord(word);
        entity.setBookId(bookId);
        entity.setCreateDate(new Date());
        dictFamiliarWordDao.insert(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unmarkFamiliar(Long familiarId) {
        Long userId = SecurityUser.getUserId();
        if (userId == null || familiarId == null) {
            return;
        }
        LambdaQueryWrapper<DictFamiliarWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictFamiliarWordEntity::getId, familiarId)
                .eq(DictFamiliarWordEntity::getUserId, userId);
        dictFamiliarWordDao.delete(wrapper);
    }

    @Override
    public Set<Long> getFamiliarVocabIds(Long userId, Long bookId) {
        if (userId == null) {
            return new HashSet<>();
        }
        LambdaQueryWrapper<DictFamiliarWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictFamiliarWordEntity::getUserId, userId);
        if (bookId != null) {
            wrapper.eq(DictFamiliarWordEntity::getBookId, bookId);
        }
        List<DictFamiliarWordEntity> list = dictFamiliarWordDao.selectList(wrapper);
        return list.stream()
                .map(DictFamiliarWordEntity::getVocabId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<String> getFamiliarWords(Long userId) {
        if (userId == null) {
            return new HashSet<>();
        }
        LambdaQueryWrapper<DictFamiliarWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictFamiliarWordEntity::getUserId, userId);
        List<DictFamiliarWordEntity> list = dictFamiliarWordDao.selectList(wrapper);
        return list.stream()
                .map(DictFamiliarWordEntity::getWord)
                .filter(StrUtil::isNotBlank)
                .map(w -> w.toLowerCase())
                .collect(Collectors.toSet());
    }

    @Override
    public Map<Long, Long> getFamiliarVocabIdMap(Long userId, Long bookId) {
        if (userId == null) {
            return new HashMap<>();
        }
        LambdaQueryWrapper<DictFamiliarWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictFamiliarWordEntity::getUserId, userId);
        if (bookId != null) {
            wrapper.eq(DictFamiliarWordEntity::getBookId, bookId);
        }
        List<DictFamiliarWordEntity> list = dictFamiliarWordDao.selectList(wrapper);
        Map<Long, Long> map = new HashMap<>();
        for (DictFamiliarWordEntity entity : list) {
            if (entity.getVocabId() != null) {
                map.put(entity.getVocabId(), entity.getId());
            }
        }
        return map;
    }

    @Override
    public PageData<DictVocabularyVO> pageWordsWithFamiliar(Long bookId, String word, Integer page, Integer limit) {
        List<DictVocabularyVO> list = dictVocabularyService.listWordsByBook(bookId, word, page, limit);
        long total = dictVocabularyService.countWordsByBook(bookId, word);
        if (CollUtil.isNotEmpty(list)) {
            Long userId = SecurityUser.getUserId();
            Map<Long, Long> familiarMap = getFamiliarVocabIdMap(userId, bookId);
            for (DictVocabularyVO vo : list) {
                Long familiarId = familiarMap.get(vo.getId());
                vo.setFamiliarId(familiarId);
                vo.setFamiliar(familiarId != null);
            }
        }
        return new PageData<>(list, total);
    }

    @Override
    public PageData<DictVocabularyVO> pageFamiliarWords(String word, Integer page, Integer limit) {
        Long userId = SecurityUser.getUserId();
        long pageNo = page == null ? 1 : Math.max(1, page);
        long pageSize = limit == null ? 20 : Math.max(1, limit);

        // 1. 分页查询当前用户的标熟记录（按 create_date 倒序）
        Page<DictFamiliarWordEntity> p = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<DictFamiliarWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictFamiliarWordEntity::getUserId, userId);
        if (StrUtil.isNotBlank(word)) {
            wrapper.like(DictFamiliarWordEntity::getWord, word);
        }
        wrapper.orderByDesc(DictFamiliarWordEntity::getCreateDate);
        dictFamiliarWordDao.selectPage(p, wrapper);
        List<DictFamiliarWordEntity> records = p.getRecords();
        long total = p.getTotal();

        if (CollUtil.isEmpty(records)) {
            return new PageData<>(new java.util.ArrayList<>(), total);
        }

        // 2. 批量查询词汇详情（biz_vocabularies）
        List<Long> vocabIds = records.stream()
                .map(DictFamiliarWordEntity::getVocabId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, BizVocabularyEntity> vocabMap = new HashMap<>();
        if (!vocabIds.isEmpty()) {
            LambdaQueryWrapper<BizVocabularyEntity> vocabWrapper = new LambdaQueryWrapper<>();
            vocabWrapper.in(BizVocabularyEntity::getId, vocabIds);
            List<BizVocabularyEntity> vocabEntities = bizVocabularyDao.selectList(vocabWrapper);
            for (BizVocabularyEntity e : vocabEntities) {
                vocabMap.put(e.getId(), e);
            }
        }

        // 3. 批量查询词书名称
        List<Long> bookIds = records.stream()
                .map(DictFamiliarWordEntity::getBookId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> bookNameMap = new HashMap<>();
        if (!bookIds.isEmpty()) {
            LambdaQueryWrapper<BizVocabularyBookEntity> bookWrapper = new LambdaQueryWrapper<>();
            bookWrapper.in(BizVocabularyBookEntity::getId, bookIds);
            List<BizVocabularyBookEntity> books = bizVocabularyBookDao.selectList(bookWrapper);
            for (BizVocabularyBookEntity b : books) {
                bookNameMap.put(b.getId(), b.getName());
            }
        }

        // 4. 组装 VO（保持标熟记录的顺序）
        List<DictVocabularyVO> list = new java.util.ArrayList<>(records.size());
        for (DictFamiliarWordEntity rec : records) {
            DictVocabularyVO vo;
            BizVocabularyEntity vocab = vocabMap.get(rec.getVocabId());
            if (vocab != null) {
                vo = DictVocabularyParser.parse(vocab);
            } else {
                // 词汇已被删除等异常情况，用标熟记录里的冗余 word 兜底
                vo = new DictVocabularyVO();
                vo.setWord(rec.getWord());
            }
            vo.setFamiliarId(rec.getId());
            vo.setFamiliar(true);
            vo.setBookId(rec.getBookId());
            vo.setBookName(bookNameMap.get(rec.getBookId()));
            list.add(vo);
        }
        return new PageData<>(list, total);
    }
}
