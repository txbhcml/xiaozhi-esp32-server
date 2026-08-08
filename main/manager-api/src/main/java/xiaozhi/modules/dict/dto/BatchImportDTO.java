package xiaozhi.modules.dict.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 批量导入单词请求体
 */
@Data
@Schema(description = "批量导入单词")
public class BatchImportDTO {

    @Schema(description = "要导入的英文单词列表", required = true)
    private List<String> words;

    @Schema(description = "可选，指定词书ID用于查找翻译")
    private Long bookId;
}
