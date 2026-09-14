<template>
  <div class="learn-cat-page">
    <div class="learn-cat-body">
      <!-- 左：该板块学习文章列表（无滚动条，随页面滑动） -->
      <aside class="cat-list">
        <div class="cat-list-title">{{ categoryName }}</div>
        <div v-if="loadingList" class="cat-tip">加载中…</div>
        <template v-else>
          <div
            v-for="a in articles"
            :key="a.id"
            class="cat-item"
            :class="{ active: a.slug === currentSlug }"
            @click="select(a.slug)"
          >
            {{ a.title }}
          </div>
          <div v-if="!articles.length" class="cat-tip">暂无内容</div>
        </template>
      </aside>

      <!-- 中：当前文章正文 -->
      <main class="cat-content">
        <div v-if="loadingDetail" class="cat-empty">加载中…</div>
        <article v-else-if="detail" class="cat-article">
          <img
            v-if="detail.article.coverUrl"
            :src="detail.article.coverUrl"
            :alt="detail.article.title"
            class="cat-article-cover"
          />
          <div class="cat-article-head">
            <h1 class="cat-article-title">{{ detail.article.title }}</h1>
            <router-link v-if="isAdmin" :to="`/admin/edit/${detail.article.slug}`" class="cat-edit-link">
              <el-button size="small" type="warning" plain>编辑</el-button>
            </router-link>
          </div>
          <p v-if="detail.article.summary" class="cat-article-summary">{{ detail.article.summary }}</p>
          <div ref="contentEl" class="article-body" v-html="detail.article.contentHtml || renderMarkdown(detail.article.contentMd)"></div>
        </article>
        <div v-else class="cat-empty">文章不存在或已下架</div>
      </main>

      <!-- 右：当前文章目录 -->
      <!-- 与专题 / 技术页一致：右侧复用同一个目录组件（吸顶 + 独立滚动） -->
      <TocPanel
        v-if="detail"
        :toc="detail.article.toc"
        :active-id="activeTocId"
        @select="activeTocId = $event"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import TocPanel from '@/components/article/TocPanel.vue'
import { getArticleDetail, getLearnArticlesApi, getLearnCategoriesApi } from '@/api/article'
import { enhanceCodeBlocks, highlightCodeBlocks, renderDiagrams, renderMarkdown } from '@/utils/markdown'
import type { ArticleDetailResp, ArticleListItem } from '@/types'

const route = useRoute()
const auth = useAuthStore()
const isAdmin = computed(() => auth.userInfo?.role === 'ADMIN')
const categorySlug = computed(() => String(route.params.categorySlug || ''))
const categoryName = ref('学习专题')
const articles = ref<ArticleListItem[]>([])
const currentSlug = ref('')
const detail = ref<ArticleDetailResp | null>(null)
const loadingList = ref(false)
const loadingDetail = ref(false)
const contentEl = ref<HTMLElement | null>(null)
/** 当前高亮的目录项（与专题/技术页同一套逻辑） */
const activeTocId = ref('')

async function loadList() {
  loadingList.value = true
  try {
    const [cats, res] = await Promise.all([
      getLearnCategoriesApi(),
      getLearnArticlesApi({ categorySlug: categorySlug.value, page: 1, size: 100 })
    ])
    const hit = cats.find((c) => c.slug === categorySlug.value)
    if (hit) categoryName.value = hit.name
    articles.value = res.list
    if (res.list.length && !currentSlug.value) {
      const wantedSlug = String(route.query.article || '')
      const target = wantedSlug && res.list.some((a) => a.slug === wantedSlug) ? wantedSlug : res.list[0].slug
      select(target)
    }
  } catch {
    // 拦截器已提示
  } finally {
    loadingList.value = false
  }
}

async function select(slug: string) {
  if (slug === currentSlug.value && detail.value) return
  currentSlug.value = slug
  loadingDetail.value = true
  try {
    detail.value = await getArticleDetail(slug)
  } catch {
    detail.value = null
  } finally {
    // 必须先关掉 loading 再 nextTick：模板里正文是 v-else-if="detail"，
    // loadingDetail 仍为 true 时 <div ref="contentEl"> 还没进 DOM，
    // contentEl.value 是 null，下面的高亮 / Mermaid / 复制按钮会被整段跳过。
    loadingDetail.value = false
  }
  await nextTick()
  if (contentEl.value) {
    highlightCodeBlocks(contentEl.value)
    await renderDiagrams(contentEl.value)
    enhanceCodeBlocks(contentEl.value)
  }
}

/** 滚动时高亮当前章节，和专题/技术页保持一致 */
function onScroll() {
  if (!detail.value) return
  let current = detail.value.article.toc[0]?.id || ''
  for (const item of detail.value.article.toc) {
    const el = document.getElementById(item.id)
    if (el && el.getBoundingClientRect().top <= 90) current = item.id
  }
  activeTocId.value = current
}

watch(categorySlug, () => {
  currentSlug.value = ''
  detail.value = null
  loadList()
}, { immediate: true })

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<style scoped>
.learn-cat-page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.learn-cat-body {
  flex: 1;
  width: 100%;
  /* 与专题/技术页同一套三栏布局：左侧留出侧栏偏移，右侧目录用 TocPanel */
  max-width: none;
  margin: 0 auto;
  padding: 16px var(--layout-pad-x) 16px var(--sidebar-offset);
  display: flex;
  align-items: flex-start;
  gap: 18px;
}

/* 左：文章列表，无滚动条，随页面滑动 */
.cat-list {
  width: 240px;
  flex-shrink: 0;
}

.cat-list-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--app-title-accent);
  padding: 10px 12px;
  border-left: 3px solid var(--app-accent);
  margin-bottom: 10px;
}

.cat-item {
  padding: 9px 12px;
  border-radius: 8px;
  font-size: 13px;
  color: var(--app-text);
  cursor: pointer;
  line-height: 1.5;
  transition: background 0.15s, color 0.15s;
}

.cat-item:hover {
  background: var(--app-accent-soft);
  color: var(--app-accent);
}

.cat-item.active {
  background: var(--app-accent-soft);
  color: var(--app-accent);
  font-weight: 600;
}

.cat-tip {
  padding: 12px;
  font-size: 13px;
  color: var(--app-text-secondary);
}

/* 中：正文 */
.cat-content {
  flex: 1;
  min-width: 0;
}

.cat-article {
  background: var(--app-card-translucent);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 30px 36px;
}

.cat-article-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.cat-article-cover {
  display: block;
  width: 100%;
  max-height: 340px;
  object-fit: cover;
  border-radius: 10px;
  margin-bottom: 18px;
}

.cat-edit-link {
  flex-shrink: 0;
  margin-top: 2px;
}

.cat-article-title {
  margin: 0 0 12px;
  font-size: 26px;
  font-weight: 700;
  color: var(--app-text);
  line-height: 1.4;
}

.cat-article-summary {
  margin: 0 0 22px;
  padding: 12px 16px;
  border-radius: 10px;
  background: rgba(232, 154, 31, 0.08);
  border: 1px solid rgba(232, 154, 31, 0.22);
  font-size: 13px;
  color: var(--app-text-secondary);
  line-height: 1.7;
}

.cat-empty {
  padding: 70px 20px;
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 14px;
}

@media (max-width: 980px) {
  .learn-cat-body {
    flex-direction: column;
  }

  .cat-list {
    width: 100%;
  }
}
</style>
