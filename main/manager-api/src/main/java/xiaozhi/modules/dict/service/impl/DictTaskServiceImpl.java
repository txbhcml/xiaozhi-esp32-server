package xiaozhi.modules.dict.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.dict.dao.BizVocabularyBookDao;
import xiaozhi.modules.dict.dao.DictTaskDao;
import xiaozhi.modules.dict.dao.DictTaskWordDao;
import xiaozhi.modules.dict.dto.DictTaskSaveDTO;
import xiaozhi.modules.dict.entity.BizVocabularyBookEntity;
import xiaozhi.modules.dict.entity.DictTaskEntity;
import xiaozhi.modules.dict.entity.DictTaskWordEntity;
import xiaozhi.modules.dict.service.DictTaskService;
import xiaozhi.modules.dict.vo.DictTaskVO;
import xiaozhi.modules.dict.vo.DictVocabularyVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 听写任务 Service 实现
 */
@Service
@AllArgsConstructor
public class DictTaskServiceImpl extends BaseServiceImpl<DictTaskDao, DictTaskEntity>
        implements DictTaskService {

    private final DictTaskDao dictTaskDao;
    private final BizVocabularyBookDao bizVocabularyBookDao;
    private final DictTaskWordDao dictTaskWordDao;

    @Override
    public PageData<DictTaskVO> page(Map<String, Object> params) {
        Long userId = SecurityUser.getUserId();
        IPage<DictTaskEntity> page = getPage(params, "create_date", false);
        LambdaQueryWrapper<DictTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictTaskEntity::getUserId, userId)
                .orderByDesc(DictTaskEntity::getCreateDate);
        // 任务名称模糊查询
        String taskName = (String) params.get("taskName");
        if (StrUtil.isNotBlank(taskName)) {
            wrapper.like(DictTaskEntity::getTaskName, taskName);
        }
        // 状态过滤
        String status = (String) params.get("status");
        if (StrUtil.isNotBlank(status)) {
            wrapper.eq(DictTaskEntity::getStatus, Integer.parseInt(status));
        }
        dictTaskDao.selectPage(page, wrapper);
        List<DictTaskEntity> records = page.getRecords();
        List<DictTaskVO> voList = new ArrayList<>(records.size());
        // 批量查询每个任务的单词数，避免 N+1
        if (!records.isEmpty()) {
            List<String> taskIds = records.stream().map(DictTaskEntity::getId).collect(Collectors.toList());
            Map<String, Long> countMap = countWordsByTaskIds(taskIds);
            for (DictTaskEntity entity : records) {
                DictTaskVO vo = new DictTaskVO();
                vo.setId(entity.getId());
                vo.setUserId(entity.getUserId());
                vo.setTaskName(entity.getTaskName());
                vo.setBookId(entity.getBookId());
                vo.setMode(entity.getMode());
                vo.setAccent(entity.getAccent());
                vo.setIntervalSeconds(entity.getIntervalSeconds());
                vo.setRepeatCount(entity.getRepeatCount());
                vo.setSpeakRate(entity.getSpeakRate());
                vo.setRepeatIntervalSeconds(entity.getRepeatIntervalSeconds());
                vo.setIntroduceWords(toBoolFlag(entity.getIntroduceWords()));
                vo.setShowExample(toBoolFlag(entity.getShowExample()));
                vo.setExampleTranslate(toBoolFlag(entity.getExampleTranslate()));
                vo.setShowSynonym(toBoolFlag(entity.getShowSynonym()));
                vo.setStatus(entity.getStatus());
                vo.setSort(entity.getSort());
                vo.setCreateDate(entity.getCreateDate());
                vo.setUpdateDate(entity.getUpdateDate());
                vo.setWordCount(countMap.getOrDefault(entity.getId(), 0L).intValue());
                if (entity.getBookId() != null) {
                    BizVocabularyBookEntity book = bizVocabularyBookDao.selectById(entity.getBookId());
                    if (book != null) {
                        vo.setBookName(book.getName());
                    }
                }
                voList.add(vo);
            }
        }
        return new PageData<>(voList, page.getTotal());
    }

    /**
     * 批量统计多个任务的单词数
     */
    private Map<String, Long> countWordsByTaskIds(List<String> taskIds) {
        Map<String, Long> result = new HashMap<>();
        if (CollUtil.isEmpty(taskIds)) {
            return result;
        }
        LambdaQueryWrapper<DictTaskWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DictTaskWordEntity::getTaskId, taskIds);
        List<DictTaskWordEntity> all = dictTaskWordDao.selectList(wrapper);
        for (DictTaskWordEntity w : all) {
            result.merge(w.getTaskId(), 1L, Long::sum);
        }
        return result;
    }

    @Override
    public DictTaskVO getDetail(String id) {
        DictTaskEntity entity = dictTaskDao.selectById(id);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTaskVO save(DictTaskSaveDTO dto) {
        Long userId = SecurityUser.getUserId();
        DictTaskEntity entity;
        boolean isUpdate = StrUtil.isNotBlank(dto.getId());
        if (isUpdate) {
            entity = dictTaskDao.selectById(dto.getId());
            if (entity == null) {
                throw new RuntimeException("听写任务不存在：" + dto.getId());
            }
            if (!entity.getUserId().equals(userId)) {
                throw new RuntimeException("无权修改此听写任务");
            }
        } else {
            entity = new DictTaskEntity();
            entity.setUserId(userId);
            entity.setCreator(userId);
            entity.setCreateDate(new Date());
        }

        entity.setTaskName(dto.getTaskName());
        entity.setBookId(dto.getBookId());
        entity.setMode(dto.getMode());
        entity.setAccent(StrUtil.isBlank(dto.getAccent()) ? "us" : dto.getAccent());
        entity.setIntervalSeconds(dto.getIntervalSeconds());
        entity.setRepeatCount(dto.getRepeatCount() == null ? 1 : dto.getRepeatCount());
        entity.setSpeakRate(dto.getSpeakRate() == null ? 0 : dto.getSpeakRate());
        entity.setRepeatIntervalSeconds(dto.getRepeatIntervalSeconds() == null ? new BigDecimal("1.0") : dto.getRepeatIntervalSeconds());
        entity.setIntroduceWords(toIntFlag(dto.getIntroduceWords()));
        entity.setShowExample(toIntFlag(dto.getShowExample()));
        entity.setExampleTranslate(toIntFlag(dto.getExampleTranslate()));
        entity.setShowSynonym(toIntFlag(dto.getShowSynonym()));
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());

        entity.setUpdater(userId);
        entity.setUpdateDate(new Date());

        if (isUpdate) {
            dictTaskDao.updateById(entity);
        } else {
            dictTaskDao.insert(entity);
        }

        // 单词列表存入附表：先删后插
        LambdaQueryWrapper<DictTaskWordEntity> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(DictTaskWordEntity::getTaskId, entity.getId());
        dictTaskWordDao.delete(delWrapper);
        if (CollUtil.isNotEmpty(dto.getWords())) {
            int sort = 0;
            List<DictTaskWordEntity> wordEntities = new ArrayList<>();
            for (DictVocabularyVO w : dto.getWords()) {
                if (StrUtil.isBlank(w.getWord())) {
                    continue;
                }
                DictTaskWordEntity we = new DictTaskWordEntity();
                we.setTaskId(entity.getId());
                we.setVocabId(w.getId());
                we.setWord(w.getWord());
                we.setMeaning(w.getMeaning());
                we.setSource("book".equalsIgnoreCase(w.getSource()) ? "book" : "manual");
                we.setSort(sort++);
                we.setCreateDate(new Date());
                wordEntities.add(we);
            }
            if (!wordEntities.isEmpty()) {
                for (DictTaskWordEntity we : wordEntities) {
                    dictTaskWordDao.insert(we);
                }
            }
        }

        // 同一用户只能有一个启用的听写任务：启用当前任务时，禁用其它任务
        if (Integer.valueOf(1).equals(entity.getStatus())) {
            LambdaQueryWrapper<DictTaskEntity> disableWrapper = new LambdaQueryWrapper<>();
            disableWrapper.eq(DictTaskEntity::getUserId, userId)
                    .eq(DictTaskEntity::getStatus, 1)
                    .ne(DictTaskEntity::getId, entity.getId());
            DictTaskEntity disable = new DictTaskEntity();
            disable.setStatus(0);
            disable.setUpdater(userId);
            disable.setUpdateDate(new Date());
            dictTaskDao.update(disable, disableWrapper);
        }

        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Long userId = SecurityUser.getUserId();
        DictTaskEntity entity = dictTaskDao.selectById(id);
        if (entity == null) {
            return;
        }
        if (!entity.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此听写任务");
        }
        // 删除附表单词
        LambdaQueryWrapper<DictTaskWordEntity> delWrapper = new LambdaQueryWrapper<>();
        delWrapper.eq(DictTaskWordEntity::getTaskId, id);
        dictTaskWordDao.delete(delWrapper);
        dictTaskDao.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, Integer status) {
        Long userId = SecurityUser.getUserId();
        DictTaskEntity entity = dictTaskDao.selectById(id);
        if (entity == null) {
            return;
        }
        if (!entity.getUserId().equals(userId)) {
            throw new RuntimeException("无权修改此听写任务");
        }
        DictTaskEntity update = new DictTaskEntity();
        update.setId(id);
        update.setStatus(status);
        update.setUpdater(userId);
        update.setUpdateDate(new Date());
        dictTaskDao.updateById(update);

        // 同一用户只能有一个启用的听写任务
        if (Integer.valueOf(1).equals(status)) {
            LambdaQueryWrapper<DictTaskEntity> disableWrapper = new LambdaQueryWrapper<>();
            disableWrapper.eq(DictTaskEntity::getUserId, userId)
                    .eq(DictTaskEntity::getStatus, 1)
                    .ne(DictTaskEntity::getId, id);
            DictTaskEntity disable = new DictTaskEntity();
            disable.setStatus(0);
            disable.setUpdater(userId);
            disable.setUpdateDate(new Date());
            dictTaskDao.update(disable, disableWrapper);
        }
    }

    @Override
    public DictTaskVO getActiveTask(Long userId, String taskName) {
        if (userId == null) {
            return null;
        }
        LambdaQueryWrapper<DictTaskEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictTaskEntity::getUserId, userId)
                .eq(DictTaskEntity::getStatus, 1)
                .orderByDesc(DictTaskEntity::getCreateDate);
        if (StrUtil.isNotBlank(taskName)) {
            // 精确匹配任务名
            wrapper.eq(DictTaskEntity::getTaskName, taskName);
        }
        wrapper.last("LIMIT 1");
        DictTaskEntity entity = dictTaskDao.selectOne(wrapper);
        return toVO(entity);
    }

    private DictTaskVO toVO(DictTaskEntity entity) {
        if (entity == null) {
            return null;
        }
        DictTaskVO vo = new DictTaskVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setTaskName(entity.getTaskName());
        vo.setBookId(entity.getBookId());
        vo.setMode(entity.getMode());
        vo.setAccent(entity.getAccent());
        vo.setIntervalSeconds(entity.getIntervalSeconds());
        vo.setRepeatCount(entity.getRepeatCount());
        vo.setSpeakRate(entity.getSpeakRate());
        vo.setRepeatIntervalSeconds(entity.getRepeatIntervalSeconds());
        vo.setIntroduceWords(toBoolFlag(entity.getIntroduceWords()));
        vo.setShowExample(toBoolFlag(entity.getShowExample()));
        vo.setExampleTranslate(toBoolFlag(entity.getExampleTranslate()));
        vo.setShowSynonym(toBoolFlag(entity.getShowSynonym()));
        vo.setStatus(entity.getStatus());
        vo.setSort(entity.getSort());
        vo.setCreateDate(entity.getCreateDate());
        vo.setUpdateDate(entity.getUpdateDate());

        // 词书名称
        if (entity.getBookId() != null) {
            BizVocabularyBookEntity book = bizVocabularyBookDao.selectById(entity.getBookId());
            if (book != null) {
                vo.setBookName(book.getName());
            }
        }

        // 单词列表从附表读取
        List<DictVocabularyVO> words = resolveWords(entity.getId());
        vo.setWords(words);
        vo.setWordCount(words.size());
        return vo;
    }

    /**
     * 从附表查询任务单词列表
     */
    private List<DictVocabularyVO> resolveWords(String taskId) {
        if (StrUtil.isBlank(taskId)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<DictTaskWordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictTaskWordEntity::getTaskId, taskId)
                .orderByAsc(DictTaskWordEntity::getSort);
        List<DictTaskWordEntity> entities = dictTaskWordDao.selectList(wrapper);
        List<DictVocabularyVO> words = new ArrayList<>(entities.size());
        for (DictTaskWordEntity e : entities) {
            DictVocabularyVO vo = new DictVocabularyVO();
            vo.setId(e.getVocabId());
            vo.setWord(e.getWord());
            vo.setMeaning(e.getMeaning());
            vo.setSource(e.getSource());
            words.add(vo);
        }
        return words;
    }

    private static Integer toIntFlag(Boolean flag) {
        return Boolean.TRUE.equals(flag) ? 1 : 0;
    }

    private static Boolean toBoolFlag(Integer flag) {
        return flag != null && flag == 1;
    }
}
