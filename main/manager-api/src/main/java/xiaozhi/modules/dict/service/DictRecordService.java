package xiaozhi.modules.dict.service;

import java.util.Map;

import xiaozhi.common.page.PageData;
import xiaozhi.modules.dict.dto.DictRecordReportDTO;
import xiaozhi.modules.dict.vo.DictRecordVO;

/**
 * 听写记录 Service
 */
public interface DictRecordService {

    /**
     * 分页查询当前用户的听写记录
     */
    PageData<DictRecordVO> page(Map<String, Object> params);

    /**
     * 获取听写记录详情
     */
    DictRecordVO getDetail(String id);

    /**
     * 删除听写记录
     */
    void delete(String id);

    /**
     * Python 服务端上报听写记录（通过 macAddress 反查 user_id）
     */
    DictRecordVO report(DictRecordReportDTO dto);
}
