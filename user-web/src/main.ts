import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'highlight.js/styles/atom-one-dark.css'
import App from './App.vue'
import router from './router'
import './styles/theme.css'
import { enableImageLightbox } from './utils/lightbox'
import { enableBodyLinkHandler, enableCodeCopy } from './utils/markdown'
import { attachScrollMemory } from './utils/scroll'
import { useTheme } from './composables/useTheme'

/**
 * 清理历史版本遗留在浏览器里的无用键（功能已移除 / 旧格式）。
 * 只在启动时跑一次，避免这些数据一直占着存储。
 */
function cleanupObsoleteStorage(): void {
  try {
    // 已移除的功能：编辑用户弹窗的草稿
    sessionStorage.removeItem('draft:admin:user-edit')
    // 旧的滚动位置写法（现在统一用 scroll-pos:<路由>）
    for (let i = sessionStorage.length - 1; i >= 0; i--) {
      const key = sessionStorage.key(i)
      if (key && key.startsWith('scroll-pos/')) sessionStorage.removeItem(key)
    }
  } catch {
    // 存储不可用时忽略
  }
}

cleanupObsoleteStorage()

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })
// 挂载前先应用一次用户上次选择的主题：避免首屏先闪一下默认主题
useTheme().initTheme()
app.mount('#app')
enableImageLightbox()
// 全局统一处理正文 Markdown 链接：站内 SPA、外链新标签页（当前页不变）
enableBodyLinkHandler(router)
// 全局代理正文代码块的“一键复制”按钮
enableCodeCopy()
// 记录每个页面的滚动位置，刷新后不再被强制回顶
attachScrollMemory(router)
