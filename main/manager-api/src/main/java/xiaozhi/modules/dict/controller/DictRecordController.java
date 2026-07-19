package xiaozhi.modules.dict.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.dict.service.DictRecordService;
import xiaozhi.modules.dict.vo.DictRecordVO;

/**
 * 听写记录管理（前端管理后台用）
 */
@RestController
@RequestMapping("/dict/record")
@Tag(name = "听写记录管理")
@AllArgsConstructor
public class DictRecordController {

    private final DictRecordService dictRecordService;

    @GetMapping("/page")
    @Operation(summary = "分页查询当前用户的听写记录")
    @RequiresPermissions("sys:role:normal")
    @Parameters({
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true),
            @Parameter(name = "taskName", description = "任务名称（模糊匹配）"),
            @Parameter(name = "taskId", description = "任务ID精确匹配"),
    })
    public Result<PageData<DictRecordVO>> page(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        PageData<DictRecordVO> page = dictRecordService.page(params);
        return new Result<PageData<DictRecordVO>>().ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "听写记录详情")
    @RequiresPermissions("sys:role:normal")
    public Result<DictRecordVO> detail(@PathVariable String id) {
        return new Result<DictRecordVO>().ok(dictRecordService.getDetail(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除听写记录")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> delete(@PathVariable String id) {
        dictRecordService.delete(id);
        return new Result<>();
    }
}
