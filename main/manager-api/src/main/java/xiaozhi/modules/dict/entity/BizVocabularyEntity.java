package xiaozhi.modules.dict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 词汇表实体（只读，映射已有的 biz_vocabularies 表）
 */
@Data
@TableName("biz_vocabularies")
@Schema(description = "词汇表")
public class BizVocabularyEntity {

    @TableId
    @Schema(description = "单词ID")
    private Long id;

    @Schema(description = "英文单词")
    private String word;

    @Schema(description = "词书ID")
    private Long bookId;

    @Schema(description = "词频排名")
    private Integer wordRank;

    @Schema(description = "美式音标")
    private String usphone;

    @Schema(description = "英式音标")
    private String ukphone;

    @Schema(description = "英式语音标识")
    private String ukspeech;

    @Schema(description = "美式语音标识")
    private String usspeech;

    @Schema(description = "单词详情JSON（嵌套结构，含释义/例句/近反义词等）")
    private String content;

    @Schema(description = "词书编码")
    private String bookCode;

    @Schema(description = "教材ID")
    private String tid;

    @Schema(description = "单词标识")
    private String wordId;
}
