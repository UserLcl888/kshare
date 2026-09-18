<template>
  <footer class="app-footer">
    <div class="footer-row">
      <a
        class="footer-link"
        href="https://beian.miit.gov.cn/"
        target="_blank"
        rel="noopener noreferrer"
        title="工业和信息化部政务服务平台"
      >豫ICP备2026045115号</a>
      <span class="footer-item">知识分享 · 仅供学习交流使用</span>
      <a
        class="footer-link"
        href="https://github.com/UserLcl888"
        target="_blank"
        rel="noopener noreferrer"
        title="在 GitHub 上查看作者主页"
      >GitHub：UserLcl888</a>
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  padding: 18px 0 26px;
  text-align: center;
  /* 三项统一颜色：--footer-color 按主题在 styles/theme.css 里定义 */
  color: var(--footer-color, var(--app-title-accent));
  font-size: 13px;
  border-top: 1px solid var(--app-border);
  background: var(--app-card);
}

/* 一行三项居中，项与项之间留出间隔；窄屏自动换行 */
.footer-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 10px 24px;
  padding: 0 16px;
}

.footer-item {
  white-space: nowrap;
  letter-spacing: 0.2px;
}

/* 备案号 / GitHub：颜色与同行的文字一致，只有可点性不同（带下划线反馈） */
.footer-link {
  color: inherit;
  font-weight: 600;
  text-decoration: none;
  white-space: nowrap;
  transition: color 0.15s ease;
}

.footer-link:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 键盘 Tab 聚焦时也要看得见 */
.footer-link:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
  border-radius: 4px;
}
</style>
