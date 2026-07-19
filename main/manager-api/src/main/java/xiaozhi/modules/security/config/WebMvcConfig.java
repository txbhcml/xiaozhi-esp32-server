package xiaozhi.modules.security.config;

import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

/**
 * Web MVC 配置
 * Spring Boot 4 默认使用 Jackson 3，通过 JsonMapperBuilderCustomizer 定制配置
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowCredentials(true)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .maxAge(3600);
    }

    /**
     * 定制 Jackson 3 的 JsonMapper.Builder
     * Spring Boot 4 会自动应用这些定制
     */
    @Bean
    public JsonMapperBuilderCustomizer jsonMapperBuilderCustomizer() {
        return builder -> {
            // 忽略未知属性
            builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            
            // Long 类型转 String（防止前端精度丢失）
            SimpleModule simpleModule = new SimpleModule();
            simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
            simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            builder.addModule(simpleModule);
        };
    }

    /**
     * 国际化配置 - 根据请求头中的Accept-Language设置语言环境
     */
    @Bean
    public LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override
            public Locale resolveLocale(HttpServletRequest request) {
                String acceptLanguage = request.getHeader("Accept-Language");
                if (acceptLanguage == null || acceptLanguage.isEmpty()) {
                    return Locale.getDefault();
                }

                // 解析Accept-Language请求头中的首选语言
                String[] languages = acceptLanguage.split(",");
                if (languages.length > 0) {
                    // 提取第一个语言代码，去除可能的质量值(q=...)
                    String[] parts = languages[0].split(";" + "\\s*");
                    String primaryLanguage = parts[0].trim();

                    // 根据前端发送的语言代码直接创建Locale对象
                    if (primaryLanguage.equals("zh-CN")) {
                        return Locale.SIMPLIFIED_CHINESE;
                    } else if (primaryLanguage.equals("zh-TW")) {
                        return Locale.TRADITIONAL_CHINESE;
                    } else if (primaryLanguage.equals("en-US")) {
                        return Locale.US;
                    } else if (primaryLanguage.equals("de-DE")) {
                        return Locale.GERMANY;
                    } else if (primaryLanguage.equals("vi-VN")) {
                        return Locale.forLanguageTag("vi-VN");
                    } else if (primaryLanguage.startsWith("zh")) {
                        // 对于其他中文变体，默认使用简体中文
                        return Locale.SIMPLIFIED_CHINESE;
                    } else if (primaryLanguage.startsWith("en")) {
                        // 对于其他英文变体，默认使用美式英语
                        return Locale.US;
                    } else if (primaryLanguage.startsWith("de")) {
                        // 对于其他德语变体，默认使用德语
                        return Locale.GERMANY;
                    } else if (primaryLanguage.startsWith("vi")) {
                        // 对于其他越南语变体，默认使用越南语
                        return Locale.forLanguageTag("vi-VN");
                    }
                }

                // 如果没有匹配的语言，使用默认语言
                return Locale.getDefault();
            }
        };
    }

}