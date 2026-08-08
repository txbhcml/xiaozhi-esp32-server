package xiaozhi.modules.dict.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.dict.dto.DictFamiliarMarkDTO;
import xiaozhi.modules.dict.service.DictFamiliarWordService;
import xiaozhi.modules.dict.vo.DictVocabularyVO;

/**
 * 标熟单词管理（前端管理后台用）
 */
@RestController
@RequestMapping("/dict")
@Tag(name = "标熟单词管理")
@AllArgsConstructor
public class DictFamiliarWordController {

    private final DictFamiliarWordService dictFamiliarWordService;

    @PostMapping("/familiar")
    @Operation(summary = "标熟单词")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> mark(@Valid @RequestBody DictFamiliarMarkDTO dto) {
        dictFamiliarWordService.markFamiliar(dto.getVocabId(), dto.getWord(), dto.getBookId());
        return new Result<>();
    }

    @DeleteMapping("/familiar/{familiarId}")
    @Operation(summary = "取消标熟")
    @RequiresPermissions("sys:role:normal")
    public Result<Void> unmark(@PathVariable Long familiarId) {
        dictFamiliarWordService.unmarkFamiliar(familiarId);
        return new Result<>();
    }

    @GetMapping("/vocabulary/book/{bookId}/word/familiar")
    @Operation(summary = "分页查询词书单词（含标熟状态）")
    @RequiresPermissions("sys:role:normal")
    public Result<PageData<DictVocabularyVO>> pageWordsWithFamiliar(
            @PathVariable Long bookId,
            @Parameter(description = "单词模糊匹配") @RequestParam(required = false) String word,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示记录数") @RequestParam(defaultValue = "20") Integer limit) {
        return new Result<PageData<DictVocabularyVO>>().ok(
                dictFamiliarWordService.pageWordsWithFamiliar(bookId, word, page, limit));
    }

    @GetMapping("/familiar/page")
    @Operation(summary = "分页查询当前用户所有标熟单词（跨词书）")
    @RequiresPermissions("sys:role:normal")
    public Result<PageData<DictVocabularyVO>> pageFamiliarWords(
            @Parameter(description = "单词模糊匹配") @RequestParam(required = false) String word,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示记录数") @RequestParam(defaultValue = "20") Integer limit) {
        return new Result<PageData<DictVocabularyVO>>().ok(
                dictFamiliarWordService.pageFamiliarWords(word, page, limit));
    }
}
