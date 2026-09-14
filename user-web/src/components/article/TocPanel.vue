<template>
  <nav class="toc-panel" :class="{ 'toc-panel-embedded': !!scrollRoot }">
    <div class="toc-title">目录</div>
    <a
      v-for="item in toc"
      :key="item.id"
      :href="`#${item.id}`"
      class="toc-item"
      :class="{ active: active === item.id }"
      :style="{ paddingLeft: indent(item.level) + 'px', fontSize: fontSize(item.level) + 'px' }"
      @click.prevent="scrollTo(item.id)"
    >
      {{ item.text }}
    </a>
    <div v-if="!toc.length" class="toc-empty">暂无目录</div>
  </nav>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import type { TocItem } from '@/types'

const props = withDefaults(
  defineProps<{
    toc: TocItem[]
    activeId?: string
    /**
     * 滚动容器：不传 = 正文页，滚 window（吸顶页头偏移 84）；
     * 传了 = 嵌在弹窗里，只在容器内滚动并自己算高亮（弹窗内的目录用这个）。
     */
    scrollRoot?: HTMLElement | null
  }>(),
  { activeId: '', scrollRoot: null }
)
const emit = defineEmits<{ (e: 'select', id: string): void }>()

/** 正文页由父页面统一算高亮；弹窗里组件自己算 */
const innerActiveId = ref('')
const active = computed(() => (props.scrollRoot ? innerActiveId.value : props.activeId))

const minLevel = computed(() => {
  if (!props.toc.length) return 2
  return Math.min(...props.toc.map((t) => t.level))
})

function indent(level: number): number {
  return 8 + (level - minLevel.value) * 14
}

function fontSize(level: number): number {
  return Math.max(12, 14 - (level - minLevel.value))
}

/** 正文页吸顶页头高度（滚 window 时让标题落在页头下方） */
const HEADER_OFFSET = 84
/** 弹窗内目录：标题距容器顶部留一点空隙 */
const EMBEDDED_OFFSET = 12
/** 弹窗内高亮判定线：标题越过容器顶部这条线就算"当前章节" */
const SPY_LINE = 60

function scrollTo(id: string) {
  const el = document.getElementById(id)
  if (!el) return
  emit('select', id)
  const root = props.scrollRoot
  if (root) {
    innerActiveId.value = id
    const top =
      el.getBoundingClientRect().top - root.getBoundingClientRect().top + root.scrollTop - EMBEDDED_OFFSET
    root.scrollTo({ top: Math.max(0, top), behavior: 'smooth' })
    return
  }
  const targetTop = window.scrollY + el.getBoundingClientRect().top - HEADER_OFFSET
  const maxScroll = document.documentElement.scrollHeight - window.innerHeight
  const finalTop = Math.max(0, Math.min(targetTop, maxScroll))
  window.scrollTo({ top: finalTop, behavior: 'smooth' })
}

/** 弹窗里滚动时高亮当前章节（和正文页同一套"离顶部最近已越过的标题"逻辑） */
function syncActive() {
  const root = props.scrollRoot
  if (!root) return
  const rootTop = root.getBoundingClientRect().top
  let current = props.toc[0]?.id || ''
  for (const item of props.toc) {
    const el = document.getElementById(item.id)
    if (!el) continue
    if (el.getBoundingClientRect().top - rootTop <= SPY_LINE) current = item.id
  }
  innerActiveId.value = current
}

let boundRoot: HTMLElement | null = null

function bindRoot(root: HTMLElement | null) {
  if (boundRoot === root) return
  if (boundRoot) boundRoot.removeEventListener('scroll', syncActive)
  boundRoot = root
  if (boundRoot) boundRoot.addEventListener('scroll', syncActive, { passive: true })
}

watch(
  () => props.scrollRoot,
  (root) => {
    bindRoot(root)
    if (root) syncActive()
  },
  { immediate: true }
)

watch(
  () => props.toc,
  () => {
    if (props.scrollRoot) syncActive()
  }
)

onBeforeUnmount(() => bindRoot(null))
</script>

<style scoped>
.toc-panel {
  width: 240px;
  flex-shrink: 0;
  position: sticky;
  top: 72px;
  max-height: calc(100vh - 92px);
  overflow-y: auto;
  background: var(--app-card);
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 12px;
}

/* 嵌在弹窗里：不吸顶，自己滚，高度跟着弹窗正文区 */
.toc-panel-embedded {
  position: static;
  align-self: stretch;
  max-height: 68vh;
  width: 220px;
}

.toc-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text-secondary);
  padding-bottom: 8px;
  border-bottom: 1px solid var(--app-border);
  margin-bottom: 8px;
}

.toc-item {
  display: block;
  padding: 6px 8px;
  font-size: 13px;
  color: var(--app-text-body);
  border-radius: 5px;
  line-height: 1.5;
  transition: all 0.15s;
}

.toc-item:hover {
  background: var(--app-card-hover);
}

.toc-item.active {
  color: var(--app-accent);
  font-weight: 600;
  background: var(--app-accent-soft);
}

.toc-empty {
  color: var(--app-text-secondary);
  font-size: 13px;
  padding: 8px;
}
</style>
