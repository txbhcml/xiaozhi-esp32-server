package xiaozhi.modules.dict.dto;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import xiaozhi.modules.dict.vo.DictVocabularyVO;

/**
 * 听写任务保存 DTO（前端创建/更新任务时提交）
 */
@Data
@Schema(description = "听写任务保存")
public class DictTaskSaveDTO {

    @Schema(description = "任务ID（更新时必填，创建时为空）")
    private String id;

    @NotBlank(message = "任务名称不能为空")
    @Schema(description = "任务名称", required = true)
    private String taskName;

    @Schema(description = "来源词书ID（为空表示手动输入）")
    private Long bookId;

    @NotBlank(message = "播报模式不能为空")
    @Schema(description = "听写播报模式：listen_en / listen_cn", required = true)
    private String mode;

    @Schema(description = "口音：us / uk")
    private String accent;

    @Schema(description = "单词间隔时间（秒）")
    private BigDecimal intervalSeconds;

    @Schema(description = "每个单词播报次数（1~3）")
    private Integer repeatCount;

    @Schema(description = "语速调整(-100~100)")
    private Integer speakRate;

    @Schema(description = "是否在听写前介绍所有单词")
    private Boolean introduceWords;

    @Schema(description = "单词介绍阶段是否播报例句")
    private Boolean showExample;

    @Schema(description = "单词介绍阶段是否翻译例句")
    private Boolean exampleTranslate;

    @Schema(description = "单词介绍阶段是否提示近义词/反义词")
    private Boolean showSynonym;

    @Schema(description = "单词列表（词书单词带id，手动单词无id）")
    private List<DictVocabularyVO> words;

    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;
}
