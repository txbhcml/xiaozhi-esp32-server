package xiaozhi.modules.dict.vo;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 听写单词 VO（解析 biz_vocabularies.content JSON 后的结构化对象）
 * 同时用于：前端词汇展示、任务单词列表、Python /active 接口返回的单词项
 */
@Data
@Schema(description = "听写单词")
public class DictVocabularyVO {

    @Schema(description = "单词ID（来自词书时为 biz_vocabularies.id；手动输入时为空）")
    private Long id;

    @Schema(description = "英文单词")
    private String word;

    @Schema(description = "中文释义（取 content.trans[0].tranCn）")
    private String meaning;

    @Schema(description = "英文释义（取 content.trans[0].tranOther）")
    private String meaningEn;

    @Schema(description = "美式音标")
    private String phoneticUs;

    @Schema(description = "英式音标")
    private String phoneticUk;

    @Schema(description = "英文例句（取 content.sentence.sentences[0].scontent）")
    private String exampleSentence;

    @Schema(description = "例句中文翻译（取 content.sentence.sentences[0].scn）")
    private String exampleTranslation;

    @Schema(description = "近义词列表（从 content.syno.synos[].hwds[].w 提取）")
    private List<String> synonyms;

    @Schema(description = "反义词列表（从 content.antos.anto[].hwd 提取）")
    private List<String> antonyms;

    @Schema(description = "记忆方法（取 content.remMethod.val）")
    private String remMethod;
}
