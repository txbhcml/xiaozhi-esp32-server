package xiaozhi.modules.dict.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 听写记录实体（仅记录播报情况，无对错判定）
 */
@Data
@TableName("dict_record")
@Schema(description = "听写记录")
public class DictRecordEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "记录ID")
    private String id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "设备ID")
    private String deviceId;

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "任务名称（冗余）")
    private String taskName;

    @Schema(description = "总单词数")
    private Integer totalWords;

    @Schema(description = "本次播报的单词列表JSON（快照）")
    private String wordsJson;

    @Schema(description = "开始时间")
    private Date startTime;

    @Schema(description = "结束时间")
    private Date endTime;

    @Schema(description = "听写时长（秒）")
    private Integer durationSeconds;

    @Schema(description = "创建时间")
    private Date createDate;
}
