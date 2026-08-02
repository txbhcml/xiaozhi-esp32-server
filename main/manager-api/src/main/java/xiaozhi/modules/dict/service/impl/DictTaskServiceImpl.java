package xiaozhi.modules.dict.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;

import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.dict.dao.BizVocabularyBookDao;
import xiaozhi.modules.dict.dao.DictTaskDao;
import xiaozhi.modules.dict.dto.DictTaskSaveDTO;
import xiaozhi.modules.dict.entity.BizVocabularyBookEntity;
import xiaozhi.modules.dict.entity.DictTaskEntity;
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
        List<DictTaskVO> voList = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageData<>(voList, page.getTotal());
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
        entity.setIntroduceWords(toIntFlag(dto.getIntroduceWords()));
        entity.setShowExample(toIntFlag(dto.getShowExample()));
        entity.setExampleTranslate(toIntFlag(dto.getExampleTranslate()));
        entity.setShowSynonym(toIntFlag(dto.getShowSynonym()));
        entity.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        entity.setSort(dto.getSort() == null ? 0 : dto.getSort());

        // 统一用 words_json 存储所有单词（含词书单词的 id）
        if (CollUtil.isNotEmpty(dto.getWords())) {
            entity.setWordsJson(JSONUtil.toJsonStr(dto.getWords()));
        } else {
            entity.setWordsJson(null);
        }

        entity.setUpdater(userId);
        entity.setUpdateDate(new Date());

        if (isUpdate) {
            dictTaskDao.updateById(entity);
        } else {
            dictTaskDao.insert(entity);
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

        // 解析单词列表（统一从 words_json 反序列化）
        List<DictVocabularyVO> words = resolveWords(entity);
        vo.setWords(words);
        vo.setWordCount(words == null ? 0 : words.size());
        return vo;
    }

    /**
     * 解析任务的单词列表（统一从 words_json 反序列化）
     */
    private List<DictVocabularyVO> resolveWords(DictTaskEntity entity) {
        if (StrUtil.isNotBlank(entity.getWordsJson())) {
            return JSONUtil.toList(entity.getWordsJson(), DictVocabularyVO.class);
        }
        return new ArrayList<>();
    }

    private static Integer toIntFlag(Boolean flag) {
        return Boolean.TRUE.equals(flag) ? 1 : 0;
    }

    private static Boolean toBoolFlag(Integer flag) {
        return flag != null && flag == 1;
    }
}
