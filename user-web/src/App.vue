<template>
  <div class="app-shell">
    <!-- 全局背景：登录页背景图 + 暗色渐变，铺满几乎所有页面 -->
    <div class="app-bg" aria-hidden="true"></div>
    <div class="app-view">
      <!-- 不加过场动画：点击导航立即切换，顶部导航栏由布局保持不动 -->
      <router-view />
    </div>
  </div>
</template>

<script setup lang="ts">
// 主题初始化已提前到 main.ts（挂载前应用），避免首屏闪一下默认主题
</script>

<style>
.app-shell {
  position: relative;
  min-height: 100%;
}

.app-bg {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(1100px 720px at 18% 8%, rgba(31, 47, 74, 0.5) 0%, rgba(31, 47, 74, 0) 62%),
    radial-gradient(760px 560px at 88% 88%, rgba(20, 30, 48, 0.55) 0%, rgba(20, 30, 48, 0) 60%),
    linear-gradient(135deg, #0a0e17 0%, #070b12 45%, #05080f 100%);
}

.app-bg::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  /* 背景插画与作者头像共用 public/logo.webp（WebP 68KB，原 PNG 2MB）。
     opacity 先为 0，等 index.html 里的内联脚本在图片加载完成后给 html 加 .bg-ready 再淡入，
     这样首屏不会被这张图挡住。 */
  background: url('/logo.webp') center / cover no-repeat;
  opacity: 0;
  transition: opacity 0.5s ease;
  filter: brightness(1.25) contrast(1.05) saturate(1.05);
}

html.bg-ready .app-bg::after {
  opacity: 0.16;
}

.app-view {
  position: relative;
  z-index: 1;
}

</style>
