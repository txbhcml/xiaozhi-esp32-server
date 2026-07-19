package xiaozhi.modules.dict.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.dict.dto.DictRecordReportDTO;
import xiaozhi.modules.dict.service.DictRecordService;
import xiaozhi.modules.dict.service.DictTaskService;
import xiaozhi.modules.dict.vo.DictRecordVO;
import xiaozhi.modules.dict.vo.DictTaskVO;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;

/**
 * Python 服务端调用的听写接口（使用 server filter，无需用户登录态）
 * <ul>
 *   <li>GET  /dict/active          - 获取当前生效的听写任务（含单词列表）</li>
 *   <li>POST /dict/record/report   - 上报听写记录</li>
 * </ul>
 */
@RestController
@RequestMapping("/dict")
@Tag(name = "听写服务接口（Python 端）")
@AllArgsConstructor
public class DictActiveController {

    private final DictTaskService dictTaskService;
    private final DictRecordService dictRecordService;
    private final DeviceService deviceService;

    @GetMapping("/active")
    @Operation(summary = "获取当前生效的听写任务（含单词列表）")
    public Result<DictTaskVO> getActiveTask(
            @RequestParam String macAddress,
            @RequestParam(required = false) String taskName) {
        DeviceEntity device = deviceService.getDeviceByMacAddress(macAddress);
        if (device == null || device.getUserId() == null) {
            return new Result<DictTaskVO>().error("设备未找到或未绑定用户：" + macAddress);
        }
        DictTaskVO vo = dictTaskService.getActiveTask(device.getUserId(), taskName);
        if (vo == null) {
            return new Result<DictTaskVO>().error("未找到启用的听写任务");
        }
        return new Result<DictTaskVO>().ok(vo);
    }

    @PostMapping("/record/report")
    @Operation(summary = "上报听写记录（Python 服务端调用）")
    public Result<DictRecordVO> report(@Valid @RequestBody DictRecordReportDTO dto) {
        DictRecordVO vo = dictRecordService.report(dto);
        return new Result<DictRecordVO>().ok(vo);
    }
}
