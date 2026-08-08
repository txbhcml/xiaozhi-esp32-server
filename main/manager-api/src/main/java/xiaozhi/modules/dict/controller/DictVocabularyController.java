package xiaozhi.modules.dict.controller;

import java.util.List;
import java.util.Set;

import org.apache.shiro.authz.annotation.RequiresPermissions;
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
import lombok.AllArgsConstructor;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.dict.dto.BatchImportDTO;
import xiaozhi.modules.dict.service.DictFamiliarWordService;
import xiaozhi.modules.dict.service.DictVocabularyService;
import xiaozhi.modules.dict.vo.BizVocabularyBookVO;
import xiaozhi.modules.dict.vo.DictVocabularyVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 词汇查询（前端管理后台用，只读）
 */
@RestController
@RequestMapping("/dict/vocabulary")
@Tag(name = "词汇查询")
@AllArgsConstructor
public class DictVocabularyController {

    private final DictVocabularyService dictVocabularyService;
    private final DictFamiliarWordService dictFamiliarWordService;

    @GetMapping("/book/list")
    @Operation(summary = "列出所有词书")
    @RequiresPermissions("sys:role:normal")
    public Result<List<BizVocabularyBookVO>> listBooks() {
        return new Result<List<BizVocabularyBookVO>>().ok(dictVocabularyService.listBooks());
    }

    @GetMapping("/book/{bookId}/word/page")
    @Operation(summary = "分页查询词书内单词")
    @RequiresPermissions("sys:role:normal")
    public Result<PageData<DictVocabularyVO>> pageWords(
            @PathVariable Long bookId,
            @Parameter(description = "单词模糊匹配") @RequestParam(required = false) String word,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") Integer page,
            @Parameter(description = "每页显示记录数") @RequestParam(defaultValue = "20") Integer limit,
            @Parameter(description = "是否排除已标熟单词") @RequestParam(defaultValue = "false") Boolean excludeFamiliar) {
        Set<Long> excludeIds = null;
        if (Boolean.TRUE.equals(excludeFamiliar)) {
            Long userId = SecurityUser.getUserId();
            excludeIds = dictFamiliarWordService.getFamiliarVocabIds(userId, bookId);
        }
        List<DictVocabularyVO> list = dictVocabularyService.listWordsByBook(bookId, word, page, limit, excludeIds);
        long total = dictVocabularyService.countWordsByBook(bookId, word, excludeIds);
        return new Result<PageData<DictVocabularyVO>>().ok(new PageData<>(list, total));
    }

    @GetMapping("/word/list")
    @Operation(summary = "按 ID 批量查询单词（解析 content JSON）")
    @RequiresPermissions("sys:role:normal")
    public Result<List<DictVocabularyVO>> listWordsByIds(@RequestParam List<Long> ids) {
        return new Result<List<DictVocabularyVO>>().ok(dictVocabularyService.listWordsByIds(ids));
    }

    @PostMapping("/batch-import")
    @Operation(summary = "批量导入单词（词书查找 + LLM翻译）")
    @RequiresPermissions("sys:role:normal")
    public Result<List<DictVocabularyVO>> batchImport(@RequestBody BatchImportDTO dto) {
        return new Result<List<DictVocabularyVO>>().ok(dictVocabularyService.batchImport(dto));
    }
}
