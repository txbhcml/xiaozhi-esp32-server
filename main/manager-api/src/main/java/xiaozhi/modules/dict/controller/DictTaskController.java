package xiaozhi.modules.dict.controller;

import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.dict.dto.DictTaskSaveDTO;
import xiaozhi.modules.dict.service.DictTaskService;
import xiaozhi.modules.dict.vo.DictTaskVO;

/**
 * 听写任务管理（前端管理后台用）
 */
@RestController
@RequestMapping("/dict/task")
@Tag(name = "听写任务管理")
@AllArgsConstructor
public class DictTaskController {

    private final DictTaskService dictTaskService;

    @GetMapping("/page")
    @Operation(summary = "分页查询当前用户的听写任务")
    @RequiresPermissions("sys:role:normal")
    @Parameters({
            @Parameter(name = Constant.PAGE, description = "当前页码，从1开始", required = true),
            @Parameter(name = Constant.LIMIT, description = "每页显示记录数", required = true),
            @Parameter(name = "taskName", description = "任务名称（模糊匹配）"),
            @Parameter(name = "status", description = "状态：0禁用 1启用"),
    })
    public Result<PageData<DictTaskVO>> page(
            @Parameter(hidden = true) @RequestParam Map<String, Object> params) {
        PageData<DictTaskVO> page = dictTaskService.page(params);
        return new Result<PageData<DictTaskVO>>().ok(page);
    }

    @GetMapping("/{id}")
    @Operation(summary = "听写任务详情")
    @RequiresPermissions("sys:role:normal")
    public Result<DictTaskVO> detail(@PathVariable String id) {
        return new Result<DictTaskVO>().ok(dictTaskService.getDetail(id));
    }

    @PostMapping
    @Operation(summary = "创建/更新听写任务（id 为空时创建，非空时更新）")
    @RequiresPermissions("sys:role:normal")
    public Result<DictTaskVO> save(@Valid @RequestBody DictTaskSaveDTO dto) {
        return new Result<DictTaskVO>().ok(dictTaskService.save(dto));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除听写任务")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> delete(@PathVariable String id) {
        dictTaskService.delete(id);
        return new Result<>();
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新听写任务状态")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        dictTaskService.updateStatus(id, status);
        return new Result<>();
    }
}
