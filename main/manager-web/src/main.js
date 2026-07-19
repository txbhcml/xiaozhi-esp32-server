import 'element-plus/dist/index.css';
import 'normalize.css/normalize.css';
import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import * as ElementPlusIconsVue from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox, ElNotification, ElLoading } from 'element-plus';
import App from './App.vue';
import router from './router';
import store from './store';
import i18n from './i18n';
import { getElLocale } from './i18n';
import eventBus from './utils/eventBus';
import './styles/global.scss';
import { register as registerServiceWorker } from './registerServiceWorker';

// 创建 Vue 3 应用实例
const app = createApp(App)

// 注册 Element Plus，传入当前语言的 locale
app.use(ElementPlus, { locale: getElLocale() })

// 全局注册 Element Plus 图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

// 使用路由、状态管理、国际化
app.use(router)
app.use(store)
app.use(i18n)

// 挂载事件总线到全局属性（替代 Vue 2 的 Vue.prototype.$eventBus）
app.config.globalProperties.$eventBus = eventBus

// === 兼容层：将 Element Plus 的实例方法挂载到全局属性 ===
// 这样原有代码中的 this.$message()、this.$confirm() 等调用无需修改
app.config.globalProperties.$message = ElMessage
app.config.globalProperties.$msgbox = ElMessageBox
app.config.globalProperties.$alert = ElMessageBox.alert
app.config.globalProperties.$confirm = ElMessageBox.confirm
app.config.globalProperties.$prompt = ElMessageBox.prompt
app.config.globalProperties.$notify = ElNotification
app.config.globalProperties.$loading = ElLoading.service

// 注册 Service Worker
registerServiceWorker();

// 挂载应用
app.mount('#app')
