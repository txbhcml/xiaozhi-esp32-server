package xiaozhi.modules.dict.vo;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 听写任务 VO（含单词列表）
 */
@Data
@Schema(description = "听写任务")
public class DictTaskVO {

    @Schema(description = "任务ID")
    private String id;

    @Schema(description = "所属用户ID")
    private Long userId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "来源词书ID（为空表示手动输入）")
    private Long bookId;

    @Schema(description = "来源词书名称（冗余，前端展示用）")
    private String bookName;

    @Schema(description = "听写播报模式：listen_en(播报英文单词) / listen_cn(播报中文释义)")
    private String mode;

    @Schema(description = "口音：us(美式) / uk(英式)")
    private String accent;

    @Schema(description = "单词间隔时间（秒）")
    private BigDecimal intervalSeconds;

    @Schema(description = "每个单词播报次数（1~3）")
    private Integer repeatCount;

    @Schema(description = "语速调整(-100~100)")
    private Integer speakRate;

    @Schema(description = "是否在听写前介绍所有单词：false否 true是")
    private Boolean introduceWords;

    @Schema(description = "单词介绍阶段是否播报例句：false否 true是")
    private Boolean showExample;

    @Schema(description = "单词介绍阶段是否翻译例句：false否 true是")
    private Boolean exampleTranslate;

    @Schema(description = "单词介绍阶段是否提示近义词/反义词：false否 true是")
    private Boolean showSynonym;

    @Schema(description = "单词列表（词书单词带id，手动单词无id）")
    private List<DictVocabularyVO> words;

    @Schema(description = "单词总数（前端列表展示用）")
    private Integer wordCount;

    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "创建时间")
    private Date createDate;

    @Schema(description = "更新时间")
    private Date updateDate;
}
