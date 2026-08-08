package xiaozhi.modules.dict.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 听写任务单词附表实体
 */
@Data
@TableName("dict_task_word")
@Schema(description = "听写任务单词")
public class DictTaskWordEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @Schema(description = "主键ID")
    private String id;

    @Schema(description = "所属任务ID")
    private String taskId;

    @Schema(description = "词书单词ID（来自词书时非空）")
    private Long vocabId;

    @Schema(description = "英文单词")
    private String word;

    @Schema(description = "中文释义")
    private String meaning;

    @Schema(description = "来源：book(词书) / manual(手动输入)")
    private String source;

    @Schema(description = "排序序号")
    private Integer sort;

    @Schema(description = "创建时间")
    private Date createDate;
}
