package xiaozhi.modules.dict.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.service.impl.BaseServiceImpl;
import xiaozhi.modules.dict.dao.BizVocabularyBookDao;
import xiaozhi.modules.dict.dao.BizVocabularyDao;
import xiaozhi.modules.dict.dto.BatchImportDTO;
import xiaozhi.modules.dict.entity.BizVocabularyBookEntity;
import xiaozhi.modules.dict.entity.BizVocabularyEntity;
import xiaozhi.modules.dict.service.DictFamiliarWordService;
import xiaozhi.modules.dict.service.DictVocabularyService;
import xiaozhi.modules.dict.util.DictVocabularyParser;
import xiaozhi.modules.dict.vo.BizVocabularyBookVO;
import xiaozhi.modules.dict.vo.DictVocabularyVO;
import xiaozhi.modules.security.user.SecurityUser;

/**
 * 词汇 Service 实现
 */
@Service
@Slf4j
public class DictVocabularyServiceImpl extends BaseServiceImpl<BizVocabularyDao, BizVocabularyEntity>
        implements DictVocabularyService {

    private final BizVocabularyDao bizVocabularyDao;
    private final BizVocabularyBookDao bizVocabularyBookDao;
    private final DictFamiliarWordService dictFamiliarWordService;

    public DictVocabularyServiceImpl(BizVocabularyDao bizVocabularyDao,
                                     BizVocabularyBookDao bizVocabularyBookDao,
                                     @org.springframework.context.annotation.Lazy DictFamiliarWordService dictFamiliarWordService) {
        this.bizVocabularyDao = bizVocabularyDao;
        this.bizVocabularyBookDao = bizVocabularyBookDao;
        this.dictFamiliarWordService = dictFamiliarWordService;
    }

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();
    private static final String XIAOZHI_CONFIG_PATH =
            "/Users/chenmenglong/IdeaProjects/xiaozhi/xiaozhi-esp32-server/main/xiaozhi-server/config.yaml";

    @Override
    public List<BizVocabularyBookVO> listBooks() {
        LambdaQueryWrapper<BizVocabularyBookEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(BizVocabularyBookEntity::getSortOrder)
                .orderByAsc(BizVocabularyBookEntity::getId);
        List<BizVocabularyBookEntity> entities = bizVocabularyBookDao.selectList(wrapper);
        List<BizVocabularyBookVO> result = new ArrayList<>();
        if (CollUtil.isEmpty(entities)) {
            return result;
        }
        for (BizVocabularyBookEntity e : entities) {
            BizVocabularyBookVO vo = new BizVocabularyBookVO();
            vo.setId(e.getId());
            vo.setName(e.getName());
            vo.setCode(e.getCode());
            vo.setDescription(e.getDescription());
            vo.setTotalWords(e.getTotalWords());
            vo.setSortOrder(e.getSortOrder());
            result.add(vo);
        }
        return result;
    }

    @Override
    public List<DictVocabularyVO> listWordsByBook(Long bookId, String word, Integer page, Integer limit) {
        return listWordsByBook(bookId, word, page, limit, null);
    }

    @Override
    public List<DictVocabularyVO> listWordsByBook(Long bookId, String word, Integer page, Integer limit, Set<Long> excludeIds) {
        if (bookId == null) {
            return new ArrayList<>();
        }
        long pageNo = page == null ? 1 : Math.max(1, page);
        long pageSize = limit == null ? 20 : Math.max(1, limit);
        Page<BizVocabularyEntity> p = new Page<>(pageNo, pageSize);

        LambdaQueryWrapper<BizVocabularyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizVocabularyEntity::getBookId, bookId)
                .orderByAsc(BizVocabularyEntity::getWordRank)
                .orderByAsc(BizVocabularyEntity::getId);
        if (StrUtil.isNotBlank(word)) {
            wrapper.like(BizVocabularyEntity::getWord, word);
        }
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn(BizVocabularyEntity::getId, excludeIds);
        }
        bizVocabularyDao.selectPage(p, wrapper);
        return p.getRecords().stream()
                .map(DictVocabularyParser::parse)
                .collect(Collectors.toList());
    }

    @Override
    public long countWordsByBook(Long bookId, String word) {
        return countWordsByBook(bookId, word, null);
    }

    @Override
    public long countWordsByBook(Long bookId, String word, Set<Long> excludeIds) {
        if (bookId == null) {
            return 0L;
        }
        LambdaQueryWrapper<BizVocabularyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(BizVocabularyEntity::getBookId, bookId);
        if (StrUtil.isNotBlank(word)) {
            wrapper.like(BizVocabularyEntity::getWord, word);
        }
        if (excludeIds != null && !excludeIds.isEmpty()) {
            wrapper.notIn(BizVocabularyEntity::getId, excludeIds);
        }
        return bizVocabularyDao.selectCount(wrapper);
    }

    @Override
    public List<DictVocabularyVO> listWordsByIds(List<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<BizVocabularyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizVocabularyEntity::getId, ids);
        List<BizVocabularyEntity> entities = bizVocabularyDao.selectList(wrapper);
        Map<Long, BizVocabularyEntity> idMap = entities.stream()
                .collect(Collectors.toMap(BizVocabularyEntity::getId, e -> e, (a, b) -> a));
        List<DictVocabularyVO> result = new ArrayList<>();
        for (Long id : ids) {
            BizVocabularyEntity vocab = idMap.get(id);
            if (vocab != null) {
                result.add(DictVocabularyParser.parse(vocab));
            }
        }
        return result;
    }

    @Override
    public List<DictVocabularyVO> batchImport(BatchImportDTO dto) {
        List<DictVocabularyVO> result = new ArrayList<>();
        if (dto == null || CollUtil.isEmpty(dto.getWords())) {
            return result;
        }
        List<String> words = dto.getWords().stream()
                .filter(StrUtil::isNotBlank)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
        if (words.isEmpty()) {
            return result;
        }

        // 0. 过滤掉当前用户已标熟的单词（跨词书）
        Long userId = SecurityUser.getUserId();
        if (userId != null) {
            Set<String> familiarWords = dictFamiliarWordService.getFamiliarWords(userId);
            words = words.stream()
                    .filter(w -> !familiarWords.contains(w.toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (words.isEmpty()) {
            return result;
        }

        // 1. 跨所有词书查找（不按 bookId 过滤）
        LambdaQueryWrapper<BizVocabularyEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(BizVocabularyEntity::getWord, words);
        List<BizVocabularyEntity> entities = bizVocabularyDao.selectList(wrapper);
        Map<String, BizVocabularyEntity> wordMap = new HashMap<>();
        for (BizVocabularyEntity e : entities) {
            if (StrUtil.isNotBlank(e.getWord())) {
                wordMap.putIfAbsent(e.getWord().toLowerCase(), e);
            }
        }
        // 2. 未命中的单词走 LLM 翻译
        List<String> notFound = words.stream()
                .filter(w -> !wordMap.containsKey(w.toLowerCase()))
                .collect(Collectors.toList());
        Map<String, String> translated = notFound.isEmpty()
                ? Collections.emptyMap()
                : translateWords(notFound, loadDeepSeekConfig());
        // 3. 按输入顺序组装结果（只含 word + meaning）
        for (String w : words) {
            BizVocabularyEntity entity = wordMap.get(w.toLowerCase());
            if (entity != null) {
                result.add(DictVocabularyParser.parse(entity));
            } else {
                DictVocabularyVO vo = new DictVocabularyVO();
                vo.setWord(w);
                vo.setMeaning(translated.get(w.toLowerCase()));
                result.add(vo);
            }
        }
        return result;
    }

    /**
     * DeepSeek 配置（从 xiaozhi-server config.yaml 的 DeepSeekLLM 段读取）
     */
    private record DeepSeekConfig(String baseUrl, String apiKey, String model) {
    }

    /**
     * 解析 config.yaml 中 DeepSeekLLM 段，提取 base_url / api_key / model_name
     */
    private DeepSeekConfig loadDeepSeekConfig() {
        String baseUrl = null;
        String apiKey = null;
        String model = null;
        try {
            List<String> lines = Files.readAllLines(Path.of(XIAOZHI_CONFIG_PATH), StandardCharsets.UTF_8);
            int sectionIndent = -1;
            boolean inSection = false;
            for (String line : lines) {
                String stripped = line.strip();
                if (!inSection) {
                    if (stripped.equals("DeepSeekLLM:")) {
                        sectionIndent = line.length() - line.stripLeading().length();
                        inSection = true;
                    }
                    continue;
                }
                if (stripped.isEmpty() || stripped.startsWith("#")) {
                    continue;
                }
                int indent = line.length() - line.stripLeading().length();
                if (indent <= sectionIndent) {
                    break;
                }
                int colon = stripped.indexOf(':');
                if (colon > 0) {
                    String key = stripped.substring(0, colon).trim();
                    String value = stripped.substring(colon + 1).trim();
                    int hashIdx = value.indexOf(" #");
                    if (hashIdx >= 0) {
                        value = value.substring(0, hashIdx).trim();
                    }
                    switch (key) {
                        case "api_key":
                            apiKey = value;
                            break;
                        case "model_name":
                            model = value;
                            break;
                        case "url":
                            baseUrl = value;
                            break;
                        case "base_url":
                            if (StrUtil.isBlank(baseUrl)) {
                                baseUrl = value;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (Exception e) {
            // 读取失败，返回空配置，调用方会跳过翻译
        }
        return new DeepSeekConfig(baseUrl, apiKey, model);
    }

    /**
     * 批量翻译单词（分块调用 DeepSeek Chat API）
     */
    private Map<String, String> translateWords(List<String> words, DeepSeekConfig config) {
        Map<String, String> result = new HashMap<>();
        if (CollUtil.isEmpty(words) || StrUtil.isBlank(config.apiKey())
                || StrUtil.isBlank(config.model()) || StrUtil.isBlank(config.baseUrl())) {
            return result;
        }
        int chunkSize = 50;
        for (int i = 0; i < words.size(); i += chunkSize) {
            List<String> chunk = words.subList(i, Math.min(i + chunkSize, words.size()));
            result.putAll(callDeepSeek(chunk, config));
        }
        return result;
    }

    /**
     * 调用 DeepSeek Chat API 翻译单词，返回 单词(小写) -> 中文释义
     */
    private Map<String, String> callDeepSeek(List<String> words, DeepSeekConfig config) {
        Map<String, String> result = new HashMap<>();
        try {
            String prompt = "请将以下英文单词翻译成中文，只返回翻译结果，格式：单词|翻译，每行一个。单词列表："
                    + String.join(", ", words);
            JSONObject body = new JSONObject();
            body.set("model", config.model());
            body.set("stream", false);
            JSONArray messages = new JSONArray();
            JSONObject msg = new JSONObject();
            msg.set("role", "user");
            msg.set("content", prompt);
            messages.add(msg);
            body.set("messages", messages);

            String url = config.baseUrl().replaceAll("/+$", "") + "/chat/completions";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(60))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return result;
            }
            JSONObject resp = JSONUtil.parseObj(response.body());
            JSONArray choices = resp.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return result;
            }
            JSONObject firstChoice = choices.getJSONObject(0);
            if (firstChoice == null) {
                return result;
            }
            JSONObject message = firstChoice.getJSONObject("message");
            if (message == null) {
                return result;
            }
            String content = message.getStr("content");
            if (StrUtil.isBlank(content)) {
                return result;
            }
            for (String line : content.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                int bar = line.indexOf('|');
                if (bar > 0) {
                    String w = line.substring(0, bar).trim();
                    String m = line.substring(bar + 1).trim();
                    if (StrUtil.isNotBlank(w)) {
                        result.put(w.toLowerCase(), m);
                    }
                }
            }
        } catch (Exception e) {
            // 调用失败，保留空结果（meaning 留空）
        }
        return result;
    }
}
