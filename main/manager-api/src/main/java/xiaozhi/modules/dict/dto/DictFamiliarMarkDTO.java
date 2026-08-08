package xiaozhi.modules.dict.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 标熟单词请求体
 */
@Data
@Schema(description = "标熟单词请求")
public class DictFamiliarMarkDTO {

    @NotNull(message = "词汇ID不能为空")
    @Schema(description = "词汇ID（biz_vocabularies.id）", required = true)
    private Long vocabId;

    @Schema(description = "英文单词（冗余）")
    private String word;

    @Schema(description = "词书ID")
    private Long bookId;
}
