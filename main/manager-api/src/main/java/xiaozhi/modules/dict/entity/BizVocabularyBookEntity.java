package xiaozhi.modules.dict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 词书表实体（只读，映射已有的 biz_vocabulary_books 表）
 */
@Data
@TableName("biz_vocabulary_books")
@Schema(description = "词书表")
public class BizVocabularyBookEntity {

    @TableId
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
