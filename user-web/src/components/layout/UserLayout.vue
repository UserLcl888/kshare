<template>
  <div class="user-layout">
    <!-- 顶部导航只挂载一次：切页时它保持不动，只有下方内容区替换 -->
    <AppHeader />
    <div class="layout-main">
      <router-view />
    </div>
    <AppFooter />
  </div>
</template>

<script setup lang="ts">
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
</script>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

/* 内容区撑满剩余高度：内容少的页面页脚依然贴在底部 */
.layout-main {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.layout-main > * {
  flex: 1;
}
</style>
