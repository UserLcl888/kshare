import type { Router } from 'vue-router'

/** 以路由全路径为 key，在 sessionStorage 里记录/恢复滚动位置，用于“刷新后回到原位置”。 */
const KEY = 'scroll-pos'

const positionKey = (fullPath: string) => `${KEY}:${fullPath}`
/** 记录先后顺序，用于淘汰最久没用的记录，避免 sessionStorage 越堆越多。 */
const INDEX_KEY = `${KEY}:index`
/** 最多保留多少条滚动位置记录（超出按最久未用淘汰）。 */
const MAX_ENTRIES = 30

/** 旧版本用过 scroll-pos/xxx 的写法，清理时一并处理。 */
const isPositionKey = (key: string) => key.startsWith(`${KEY}:`) || key.startsWith(`${KEY}/`)

function readIndex(): string[] {
  try {
    const raw = sessionStorage.getItem(INDEX_KEY)
    const list = raw ? (JSON.parse(raw) as unknown) : null
    return Array.isArray(list) ? list.filter((p): p is string => typeof p === 'string') : []
  } catch {
    return []
  }
}

function writeIndex(paths: string[]): void {
  try {
    sessionStorage.setItem(INDEX_KEY, JSON.stringify(paths))
  } catch {
    // 忽略
  }
}

/**
 * 记录某路由的滚动位置。
 * - 在顶部（y<=0）不记录，并顺手清掉已有记录：否则每个访问过的页面都会留下一个 0。
 * - 只保留最近 MAX_ENTRIES 条，超出的按最久未用淘汰。
 */
export function rememberScroll(fullPath: string, y: number): void {
  try {
    const pos = Math.round(y)
    const rest = readIndex().filter((p) => p !== fullPath)
    if (!(pos > 0)) {
      sessionStorage.removeItem(positionKey(fullPath))
      writeIndex(rest)
      return
    }
    sessionStorage.setItem(positionKey(fullPath), String(pos))
    rest.push(fullPath)
    while (rest.length > MAX_ENTRIES) {
      const dropped = rest.shift()
      if (dropped) sessionStorage.removeItem(positionKey(dropped))
    }
    writeIndex(rest)
  } catch {
    // sessionStorage 不可用时静默忽略
  }
}

/** 启动时清一次历史遗留：值为 0/非法 的记录、以及旧的 "scroll-pos/" 写法。 */
function cleanupStaleScrollKeys(): void {
  try {
    const stale: string[] = []
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i)
      if (!key || key === INDEX_KEY || !isPositionKey(key)) continue
      // 旧写法（scroll-pos/xxx）直接清掉：下次访问会按新 key 重新记录
      if (key.startsWith(`${KEY}/`)) {
        stale.push(key)
        continue
      }
      const value = sessionStorage.getItem(key)
      const num = value == null ? 0 : Number(value)
      if (!(num > 0)) stale.push(key)
    }
    stale.forEach((key) => sessionStorage.removeItem(key))
  } catch {
    // 忽略
  }
}

/**
 * 只保留"当前页面"的滚动记录，其它页面的一律删除。
 * 目的：换页之后上一个页面的位置就没用了，不必留着占存储；
 * 同时当前页面的记录保留下来，刷新时才能回到原来的位置。
 */
export function keepOnlyScrollKey(fullPath: string): void {
  try {
    const current = positionKey(fullPath)
    const stale: string[] = []
    for (let i = 0; i < sessionStorage.length; i++) {
      const key = sessionStorage.key(i)
      if (!key || key === INDEX_KEY || !isPositionKey(key)) continue
      if (key !== current) stale.push(key)
    }
    stale.forEach((key) => sessionStorage.removeItem(key))
    // 索引同步收敛为"只有当前页"
    writeIndex(sessionStorage.getItem(current) ? [fullPath] : [])
  } catch {
    // 忽略
  }
}

/** 读取某路由记住的滚动位置；无记录/非法值时返回 0。 */
export function readScroll(fullPath: string): number {
  try {
    const v = sessionStorage.getItem(positionKey(fullPath))
    const n = v == null ? 0 : Number(v)
    return Number.isFinite(n) && n > 0 ? Math.round(n) : 0
  } catch {
    return 0
  }
}

/**
 * 全程收集当前页滚动位置（滚动时 + 离开页面时各记一次），并关闭浏览器原生滚动恢复，
 * 交给本模块统一恢复，避免刷新后被 Vue Router 的 scrollBehavior 强制回到顶部。
 */
export function attachScrollMemory(router: Router): void {
  cleanupStaleScrollKeys()

  // 每次换页后：只留当前页面的滚动记录（上一个页面的记录删除）
  router.afterEach((to) => keepOnlyScrollKey(to.fullPath))

  let pending = 0
  const save = () => {
    // 滚动时用 requestAnimationFrame 节流，避免每个滚动事件都写一次 sessionStorage
    if (pending) return
    pending = window.requestAnimationFrame(() => {
      pending = 0
      rememberScroll(router.currentRoute.value.fullPath, window.scrollY)
    })
  }
  window.addEventListener('scroll', save, { passive: true })
  window.addEventListener('pagehide', () => {
    rememberScroll(router.currentRoute.value.fullPath, window.scrollY)
  })
  if ('scrollRestoration' in history) history.scrollRestoration = 'manual'
}

/**
 * 恢复到目标滚动位置。
 * 页面内容可能是异步渲染（文章/学习页有 loading 态）、图片也异步加载，文档高度会逐步增长；
 * 这里用 requestAnimationFrame 轮询，直到文档撑到足够高度（或超时）再一次性滚过去，
 * 避免在内容还没加载时被“按短文档高度”截到顶部。
 */
export function restoreScroll(targetY: number, maxWait = 4000): void {
  if (targetY <= 0) return
  const start = performance.now()
  const tick = () => {
    const doc = document.documentElement
    const max = doc.scrollHeight - window.innerHeight
    const canReach = max >= targetY
    const timedOut = performance.now() - start > maxWait
    if (canReach || timedOut) {
      window.scrollTo(0, Math.max(0, Math.min(targetY, max)))
      return
    }
    requestAnimationFrame(tick)
  }
  requestAnimationFrame(tick)
}
