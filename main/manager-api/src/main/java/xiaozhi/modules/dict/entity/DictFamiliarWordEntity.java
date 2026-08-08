package xiaozhi.modules.dict.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 标熟单词实体
 */
@Data
@TableName("dict_familiar_words")
@Schema(description = "标熟单词")
public class DictFamiliarWordEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "词汇ID（biz_vocabularies.id）")
    private Long vocabId;

    @Schema(description = "英文单词（冗余）")
    private String word;

    @Schema(description = "词书ID")
    private Long bookId;

    @Schema(description = "创建时间")
    private Date createDate;
}
