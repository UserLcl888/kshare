import { onBeforeUnmount, watch } from 'vue'

/**
 * 基于 sessionStorage 的轻量草稿持久化。
 *
 * 目的：在页面刷新、或是同一标签页内路由切换时，用户尚未提交的表单内容不丢失。
 * - 存储按「key」隔离，key 用 getKey() 动态计算，可在路由切换（同组件复用）时跟随变化。
 * - restoreNow()/restoreStored()：由调用方决定恢复时机。例如编辑页在异步拉取服务端数据后再恢复，让草稿优先。
 * - clear()：提交成功、取消或关闭弹窗时调用，立即清除草稿（不长期占用浏览器存储）。
 * - 存储用 sessionStorage：关掉标签页即自动清空，不会留下长期垃圾数据。
 * - 内容超出 sessionStorage 配额（例如大 base64 图片）时静默放弃保存，不影响正常提交。
 *
 * 注意：请勿把密码、验证码等敏感字段放进草稿。
 */
export function useDraftStorage(opts: {
  /** 动态计算存储 key，允许跟随路由/编辑对象变化。 */
  getKey: () => string
  /** 返回要持久化的快照（建议为 JSON 字符串）。 */
  getSnapshot: () => string
  /** 从快照恢复到页面状态。 */
  restore: (raw: string) => void
  /**
   * 超出存储配额时的降级快照（例如把体积很大的粘贴图片去掉，只保留文字部分），
   * 不提供则超配额时直接放弃保存。
   */
  getFallbackSnapshot?: () => string
  /** 变化后延迟保存的时间（毫秒），避免每敲一个字符都写存储。 */
  delay?: number
}) {
  const { getKey, getSnapshot, restore, getFallbackSnapshot, delay = 400 } = opts
  let timer: number | null = null

  const cancel = () => {
    if (timer !== null) {
      window.clearTimeout(timer)
      timer = null
    }
  }

  const save = () => {
    const key = getKey()
    try {
      sessionStorage.setItem(key, getSnapshot())
    } catch {
      // 多半是内容过大（粘贴了大图）超配额：降级保存一次，避免整份草稿都丢
      if (!getFallbackSnapshot) return
      try {
        sessionStorage.setItem(key, getFallbackSnapshot())
      } catch {
        // 仍然存不下就只能放弃，不影响正常提交
      }
    }
  }

  const stopWatch = watch(
    getSnapshot,
    (snap, prev) => {
      if (snap === prev) return
      cancel()
      timer = window.setTimeout(save, delay)
    },
    { flush: 'post' }
  )

  /** 同步恢复（初始化时用，避免异步导致被后续逻辑覆盖）。 */
  function restoreNow(): boolean {
    try {
      const raw = sessionStorage.getItem(getKey())
      if (!raw) return false
      restore(raw)
      return true
    } catch {
      return false
    }
  }

  async function restoreStored(): Promise<boolean> {
    return restoreNow()
  }

  function clear() {
    cancel()
    try {
      sessionStorage.removeItem(getKey())
    } catch {
      // 忽略
    }
  }

  onBeforeUnmount(() => {
    stopWatch()
    cancel()
  })

  return { restoreNow, restoreStored, clear }
}
