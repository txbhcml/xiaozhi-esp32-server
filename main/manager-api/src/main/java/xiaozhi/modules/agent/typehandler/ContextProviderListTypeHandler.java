package xiaozhi.modules.agent.typehandler;

import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;
import tools.jackson.core.type.TypeReference;

import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.dto.ContextProviderDTO;

public class ContextProviderListTypeHandler extends AbstractJsonTypeHandler<List<ContextProviderDTO>> {
    private static final TypeReference<List<ContextProviderDTO>> CONTEXT_PROVIDER_LIST_TYPE = new TypeReference<>() {
    };

    public ContextProviderListTypeHandler(Class<?> type) {
        super(type);
    }

    @Override
    public List<ContextProviderDTO> parse(String json) {
        if (StringUtils.isBlank(json)) {
            return Collections.emptyList();
        }
        List<ContextProviderDTO> providers = JsonUtils.parseObject(json, CONTEXT_PROVIDER_LIST_TYPE);
        return providers == null ? Collections.emptyList() : providers;
    }

    @Override
    public String toJson(List<ContextProviderDTO> obj) {
        return JsonUtils.toJsonString(obj == null ? Collections.emptyList() : obj);
    }
}
