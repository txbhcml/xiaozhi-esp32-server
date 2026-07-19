package xiaozhi.modules.dict.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.dict.dto.DictTaskSaveDTO;
import xiaozhi.modules.dict.vo.DictTaskVO;

/**
 * 听写任务 Service
 */
public interface DictTaskService {

    /**
     * 分页查询当前用户的听写任务
     */
    PageData<DictTaskVO> page(Map<String, Object> params);

    /**
     * 获取任务详情（含单词列表）
     */
    DictTaskVO getDetail(String id);

    /**
     * 创建或更新任务（id 为空时创建，非空时更新）
     */
    DictTaskVO save(DictTaskSaveDTO dto);

    /**
     * 删除任务
     */
    void delete(String id);

    /**
     * 更新任务状态
     */
    void updateStatus(String id, Integer status);

    /**
     * 获取当前生效的听写任务（Python 端调用，返回完整配置含单词列表）
     *
     * @param userId   用户ID
     * @param taskName 任务名称（为空时返回该用户的第一个启用任务）
     */
    DictTaskVO getActiveTask(Long userId, String taskName);
}
