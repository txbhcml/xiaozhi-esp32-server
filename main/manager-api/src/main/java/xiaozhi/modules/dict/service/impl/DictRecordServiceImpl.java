package xiaozhi.modules.dict.service.impl;

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
import xiaozhi.modules.dict.dao.DictRecordDao;
import xiaozhi.modules.dict.dto.DictRecordReportDTO;
import xiaozhi.modules.dict.entity.DictRecordEntity;
import xiaozhi.modules.dict.service.DictRecordService;
import xiaozhi.modules.dict.vo.DictRecordVO;
import xiaozhi.modules.dict.vo.DictVocabularyVO;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 听写记录 Service 实现
 */
@Service
@AllArgsConstructor
public class DictRecordServiceImpl extends BaseServiceImpl<DictRecordDao, DictRecordEntity>
        implements DictRecordService {

    private final DictRecordDao dictRecordDao;
    private final DeviceService deviceService;

    @Override
    public PageData<DictRecordVO> page(Map<String, Object> params) {
        Long userId = SecurityUser.getUserId();
        IPage<DictRecordEntity> page = getPage(params, "create_date", false);
        LambdaQueryWrapper<DictRecordEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DictRecordEntity::getUserId, userId)
                .orderByDesc(DictRecordEntity::getCreateDate);

        String taskName = (String) params.get("taskName");
        if (StrUtil.isNotBlank(taskName)) {
            wrapper.like(DictRecordEntity::getTaskName, taskName);
        }
        String taskId = (String) params.get("taskId");
        if (StrUtil.isNotBlank(taskId)) {
            wrapper.eq(DictRecordEntity::getTaskId, taskId);
        }
        dictRecordDao.selectPage(page, wrapper);
        List<DictRecordVO> voList = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageData<>(voList, page.getTotal());
    }

    @Override
    public DictRecordVO getDetail(String id) {
        DictRecordEntity entity = dictRecordDao.selectById(id);
        return toVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(String id) {
        Long userId = SecurityUser.getUserId();
        DictRecordEntity entity = dictRecordDao.selectById(id);
        if (entity == null) {
            return;
        }
        if (!entity.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此听写记录");
        }
        dictRecordDao.deleteById(id);
    }

    private DictRecordVO toVO(DictRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        DictRecordVO vo = new DictRecordVO();
        vo.setId(entity.getId());
        vo.setUserId(entity.getUserId());
        vo.setDeviceId(entity.getDeviceId());
        vo.setTaskId(entity.getTaskId());
        vo.setTaskName(entity.getTaskName());
        vo.setTotalWords(entity.getTotalWords());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setDurationSeconds(entity.getDurationSeconds());
        vo.setCreateDate(entity.getCreateDate());

        if (StrUtil.isNotBlank(entity.getWordsJson())) {
            vo.setWords(JSONUtil.toList(entity.getWordsJson(), DictVocabularyVO.class));
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictRecordVO report(DictRecordReportDTO dto) {
        // 通过 macAddress 反查 user_id
        DeviceEntity device = deviceService.getDeviceByMacAddress(dto.getMacAddress());
        if (device == null || device.getUserId() == null) {
            throw new RuntimeException("设备未找到或未绑定用户：" + dto.getMacAddress());
        }

        DictRecordEntity entity = new DictRecordEntity();
        entity.setUserId(device.getUserId());
        entity.setDeviceId(dto.getDeviceId());
        entity.setTaskId(dto.getTaskId());
        entity.setTaskName(dto.getTaskName());
        entity.setTotalWords(dto.getTotalWords() == null ? 0 : dto.getTotalWords());
        if (CollUtil.isNotEmpty(dto.getWords())) {
            entity.setWordsJson(JSONUtil.toJsonStr(dto.getWords()));
        }
        if (dto.getStartTime() != null) {
            entity.setStartTime(new Date(dto.getStartTime()));
        }
        if (dto.getEndTime() != null) {
            entity.setEndTime(new Date(dto.getEndTime()));
        }
        entity.setDurationSeconds(dto.getDurationSeconds());
        entity.setCreateDate(new Date());

        dictRecordDao.insert(entity);
        return toVO(entity);
    }
}
