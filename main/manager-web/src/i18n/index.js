import { createI18n } from 'vue-i18n';
import zhCN from './zh_CN';
import zhTW from './zh_TW';
import en from './en';
import de from './de';
import vi from './vi';
import ptBR from './pt_BR';

// Element Plus 语言包
import elZhCn from 'element-plus/es/locale/lang/zh-cn'
import elZhTw from 'element-plus/es/locale/lang/zh-tw'
import elEn from 'element-plus/es/locale/lang/en'
import elDe from 'element-plus/es/locale/lang/de'
import elVi from 'element-plus/es/locale/lang/vi'
import elPtBr from 'element-plus/es/locale/lang/pt-br'

import eventBus from '@/utils/eventBus';

// 应用语言代码 → Element Plus locale 映射
const elLocaleMap = {
  'zh_CN': elZhCn,
  'zh_TW': elZhTw,
  'en': elEn,
  'de': elDe,
  'vi': elVi,
  'pt_BR': elPtBr
}

// 从本地存储获取语言设置，如果没有则使用浏览器语言或默认语言
const getDefaultLanguage = () => {
  const savedLang = localStorage.getItem('userLanguage');
  if (savedLang) {
    return savedLang;
  }
  const browserLang = navigator.language || navigator.userLanguage;
  if (browserLang.indexOf('zh') === 0) {
    if (browserLang === 'zh-TW' || browserLang === 'zh-HK' || browserLang === 'zh-MO') {
      return 'zh_TW';
    }
    return 'zh_CN';
  }
  if (browserLang.indexOf('de') === 0) {
    return 'de';
  }
  if (browserLang.indexOf('vi') === 0) {
    return 'vi';
  }
  if (browserLang === 'pt-BR' || browserLang === 'pt') {
    return 'pt_BR';
  }
  return 'en';
};

// vue-i18n 9: 使用 createI18n 创建实例，legacy 模式保持 this.$t() 兼容
const i18n = createI18n({
  legacy: true,
  locale: getDefaultLanguage(),
  fallbackLocale: 'zh_CN',
  messages: {
    'zh_CN': zhCN,
    'zh_TW': zhTW,
    'en': en,
    'de': de,
    'vi': vi,
    'pt_BR': ptBR
  }
});

// 获取当前语言对应的 Element Plus locale（供 main.js 使用）
export const getElLocale = () => {
  const lang = getDefaultLanguage()
  return elLocaleMap[lang] || elEn
}

export default i18n;

// 提供一个方法来切换语言
export const changeLanguage = (lang) => {
  i18n.global.locale = lang;
  localStorage.setItem('userLanguage', lang);
  // 通知组件语言已更改（使用 mitt 事件总线）
  eventBus.emit('languageChanged', lang);
};
