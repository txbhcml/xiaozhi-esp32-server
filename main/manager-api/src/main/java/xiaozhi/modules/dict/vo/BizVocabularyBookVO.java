package xiaozhi.modules.dict.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 词书 VO（前端词书列表展示用）
 */
@Data
@Schema(description = "词书")
public class BizVocabularyBookVO {

    @Schema(description = "词书ID")
    private Long id;

    @Schema(description = "词书名称")
    private String name;

    @Schema(description = "词书编码")
    private String code;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "总词数")
    private Integer totalWords;

    @Schema(description = "排序")
    private Integer sortOrder;
}
