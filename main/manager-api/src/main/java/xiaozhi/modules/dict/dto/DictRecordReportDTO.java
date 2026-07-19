package xiaozhi.modules.dict.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xiaozhi.modules.dict.vo.DictVocabularyVO;

/**
 * 听写记录上报 DTO（Python 服务端调用，听写结束/中断时上报）
 */
@Data
@Schema(description = "听写记录上报")
public class DictRecordReportDTO {

    @NotBlank(message = "macAddress 不能为空")
    @Schema(description = "设备 MAC 地址，用于反查 user_id", required = true)
    private String macAddress;

    @Schema(description = "设备ID（可空）")
    private String deviceId;

    @NotBlank(message = "taskId 不能为空")
    @Schema(description = "听写任务ID", required = true)
    private String taskId;

    @Schema(description = "任务名称（冗余）")
    private String taskName;

    @Schema(description = "总单词数")
    private Integer totalWords;

    @Schema(description = "本次播报的单词列表（快照）")
    private List<DictVocabularyVO> words;

    @Schema(description = "开始时间（毫秒时间戳）")
    private Long startTime;

    @Schema(description = "结束时间（毫秒时间戳）")
    private Long endTime;

    @Schema(description = "听写时长（秒）")
    private Integer durationSeconds;
}
