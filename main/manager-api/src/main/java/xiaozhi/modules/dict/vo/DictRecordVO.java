package xiaozhi.modules.dict.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 听写记录 VO（含本次播报的单词列表快照）
 */
@Data
@Schema(description = "听写记录")
public class DictRecordVO {

    @Schema(description = "记录ID")
    private String id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务名称")
    private String taskName;

    @Schema(description = "总单词数")
    private Integer totalWords;

    @Schema(description = "本次播报的单词列表快照")
    private List<DictVocabularyVO> words;

    @Schema(description = "开始时间")
    private Date startTime;

    @Schema(description = "结束时间")
    private Date endTime;

    @Schema(description = "听写时长（秒）")
    private Integer durationSeconds;

    @Schema(description = "创建时间")
    private Date createDate;
}
