package xiaozhi.modules.dict.entity;

import java.math.BigDecimal;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 听写任务配置实体
 */
@Data
@TableName("dict_task")
@Schema(description = "听写任务配置")
public class DictTaskEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "任务ID")
    private String id;

    @Schema(description = "所属用户ID")
    private Long userId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "来源词书ID（为空表示手动输入）")
    private Long bookId;

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

    @Schema(description = "是否在听写前介绍所有单词：0否 1是")
    private Integer introduceWords;

    @Schema(description = "单词介绍阶段是否播报例句：0否 1是")
    private Integer showExample;

    @Schema(description = "单词介绍阶段是否翻译例句：0否 1是")
    private Integer exampleTranslate;

    @Schema(description = "单词介绍阶段是否提示近义词/反义词：0否 1是")
    private Integer showSynonym;

    @Schema(description = "手动输入的单词列表JSON（格式：[{\"word\":\"apple\",\"meaning\":\"苹果\"}]）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String wordsJson;

    @Schema(description = "从词书挑选的单词ID列表JSON（biz_vocabularies.id数组）")
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private String selectedWordIds;

    @Schema(description = "状态：0禁用 1启用")
    private Integer status;

    @Schema(description = "排序")
    private Integer sort;

    @Schema(description = "创建者")
    private Long creator;

    @Schema(description = "创建时间")
    private Date createDate;

    @Schema(description = "更新者")
    private Long updater;

    @Schema(description = "更新时间")
    private Date updateDate;
}
