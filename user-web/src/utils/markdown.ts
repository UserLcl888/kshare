import hljs from 'highlight.js'
import { Marked } from 'marked'
import DOMPurify from 'dompurify'
import type { Router } from 'vue-router'
import type { TocItem } from '@/types'

/**
 * 本地 Markdown 渲染：正文一律以服务端 contentHtml 为准（MarkdownService 渲染 + 消毒），
 * 这里只用于「预览接口不可用时的兜底」。
 *
 * 规则刻意与后端 flexmark + OWASP 白名单保持一致，避免"预览一个样、发布后另一个样"：
 * - gfm:true —— 表格 / ~~删除线~~ / - [ ] 任务列表都渲染（后端开了对应扩展）；
 * - 关掉 GFM 的裸 URL 自动链接 —— 后端没开 autolink 扩展，裸网址就该是纯文本；
 * - 标题补 id —— 与后端 addHeadingIds 同一套 slug 规则，目录锚点才能对上；
 * - 消毒白名单与后端 MarkdownService 的 allowElements / allowAttributes 一一对应。
 */
const mdParser = new Marked({ gfm: true, breaks: false })
// 覆盖 GFM 的裸 URL 分词器：https://x.com 保持纯文本（与正文一致），
// <https://x.com> 这种 CommonMark 自动链接仍然有效。
mdParser.use({ tokenizer: { url: () => undefined } })

/** 与后端 MarkdownService 的 allowElements 对齐。 */
const ALLOWED_TAGS = [
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'pre', 'code',
  'table', 'thead', 'tbody', 'tr', 'th', 'td', 'img', 'a',
  'ul', 'ol', 'li', 'blockquote', 'strong', 'em', 'del', 'br', 'hr', 'input'
]

/** 与后端 allowAttributes 对齐：每个标签只保留这些属性，其余一律删掉。 */
const ALLOWED_ATTRS: Record<string, string[]> = {
  h1: ['id'], h2: ['id'], h3: ['id'], h4: ['id'], h5: ['id'], h6: ['id'],
  code: ['class'],
  a: ['href', 'title'],
  img: ['src', 'alt', 'title'],
  input: ['type', 'checked', 'disabled']
}

let sanitizeHookReady = false

function ensureSanitizeHook(): void {
  if (sanitizeHookReady) return
  sanitizeHookReady = true
  DOMPurify.addHook('afterSanitizeAttributes', (node) => {
    const tag = node.nodeName.toLowerCase()
    const allowed = ALLOWED_ATTRS[tag] || []
    for (const attr of Array.from(node.attributes)) {
      if (!allowed.includes(attr.name)) node.removeAttribute(attr.name)
    }
  })
}

/** 与后端 MarkdownService.slugify 保持一致。 */
function slugify(text: string): string {
  const slug = text.trim().replace(/[^\p{L}\p{N}]+/gu, '-').replace(/^-+|-+$/g, '')
  return slug.length > 80 ? slug.slice(0, 80) : slug
}

/**
 * 给渲染结果里的标题补 id（marked 不生成锚点），规则与后端 addHeadingIds 一致：
 * 同名标题从第二个开始追加 -2、-3，右侧目录才能准确定位。
 */
function assignHeadingIds(html: string): { html: string; toc: TocItem[] } {
  const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html')
  const toc: TocItem[] = []
  const used = new Map<string, number>()
  doc.body.querySelectorAll('h1, h2, h3, h4, h5, h6').forEach((el) => {
    const level = Number(el.tagName.slice(1))
    const text = (el.textContent || '').trim()
    let id = slugify(text) || `sec-${toc.length + 1}`
    const count = (used.get(id) || 0) + 1
    used.set(id, count)
    if (count > 1) id = `${id}-${count}`
    el.setAttribute('id', id)
    if (text) toc.push({ id, text, level })
  })
  return { html: doc.body.innerHTML, toc }
}

/** 渲染 Markdown 为「与正文同规则」的 HTML + 目录。 */
export function renderMarkdownWithToc(md: string): { html: string; toc: TocItem[] } {
  ensureSanitizeHook()
  const raw = mdParser.parse(md || '') as string
  const clean = DOMPurify.sanitize(raw, {
    ALLOWED_TAGS,
    ALLOWED_ATTR: ['id', 'class', 'href', 'title', 'src', 'alt', 'type', 'checked', 'disabled'],
    // 允许 data:image URI：编辑器里粘贴/复制的 base64 图片，在保存前也能预览
    ADD_DATA_URI_TAGS: ['img']
  })
  return assignHeadingIds(clean)
}

/** 渲染 Markdown 为消毒后的 HTML（题目、用户上传等多处复用）。 */
export function renderMarkdown(md: string): string {
  return renderMarkdownWithToc(md).html
}

/**
 * 从已渲染的正文容器里收集目录（读 DOM 上真实的 heading id）。
 * 预览弹窗用它兜底：不论 HTML 来自服务端还是本地渲染，目录都和画面完全对上。
 */
export function collectToc(container: HTMLElement | null): TocItem[] {
  if (!container) return []
  const toc: TocItem[] = []
  container.querySelectorAll('h1, h2, h3, h4, h5, h6').forEach((el) => {
    const id = el.getAttribute('id')
    if (!id) return
    const text = (el.textContent || '').trim()
    if (!text) return
    toc.push({ id, text, level: Number(el.tagName.slice(1)) })
  })
  return toc
}

/** 为已渲染的 HTML 容器内的代码块做高亮。 */
export function highlightCodeBlocks(container: HTMLElement | null): void {
  if (!container) return
  container.querySelectorAll('pre code').forEach((block) => {
    const el = block as HTMLElement
    // Mermaid 图交由 renderDiagrams 渲染成图，不参与代码高亮
    if (/language-(mermaid|flowchart)/i.test(el.className || '')) return
    hljs.highlightElement(el)
  })
}

/** 复制文本：优先用剪贴板 API（https/localhost），不可用时退回隐藏的 textarea + execCommand。 */
async function copyText(text: string): Promise<boolean> {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text)
      return true
    } catch {
      // 非安全上下文 / 用户拒绝授权：走下面的兜底方案
    }
  }
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.top = '-1000px'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
  }
}

/* 复制按钮图标：线性风格，跟随主题色（fill:none + currentColor） */
const ICON_COPY =
  '<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" ' +
  'stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
  '<rect x="9" y="9" width="12" height="12" rx="2.5"/>' +
  '<path d="M5.5 15H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v.5"/></svg>'

const ICON_COPIED =
  '<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" ' +
  'stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
  '<path d="M20 6 9 17l-5-5"/></svg>'

/** 代码块右上角的复制按钮：常驻显示，图标常显、悬停有文字提示。 */
function createCopyButton(): HTMLButtonElement {
  const copyBtn = document.createElement('button')
  copyBtn.type = 'button'
  copyBtn.className = 'code-btn code-copy-btn'
  copyBtn.innerHTML = ICON_COPY
  copyBtn.setAttribute('data-tip', '复制代码')
  copyBtn.setAttribute('aria-label', '复制代码')
  return copyBtn
}

/**
 * 给已渲染正文里的每个代码块右上角加一个“复制”按钮。
 * - 文章详情（技术 / 文章分享）、学习专题、后台与投稿预览共用 .article-body，因此一处生效；
 * - Mermaid / flowchart 代码块会被 renderDiagrams 替换成图，这里跳过；
 * - 需在 renderDiagrams 之后调用，否则被替换掉的代码块会留下空的包裹层。
 */
export function enhanceCodeBlocks(container: HTMLElement | null): void {
  if (!container) return
  container.querySelectorAll<HTMLPreElement>('pre').forEach((pre) => {
    if (pre.closest('.code-block')) return // 已处理过
    const code = pre.querySelector('code')
    // 少数情况后端可能直接输出裸 <pre>（无 <code>），同样支持复制
    if (/language-(mermaid|flowchart)/i.test(code?.className || pre.className || '')) return

    const wrapper = document.createElement('div')
    wrapper.className = 'code-block'
    pre.parentNode?.insertBefore(wrapper, pre)
    wrapper.appendChild(pre)
    wrapper.appendChild(createCopyButton())
  })

  // 兜底：Mermaid 代码块被 renderDiagrams 换成图后，包裹层里可能只剩下图，需要拆掉空壳（保留图本身）
  container.querySelectorAll<HTMLElement>('.code-block').forEach((wrap) => {
    if (wrap.querySelector('pre')) return
    const parent = wrap.parentNode
    if (!parent) return
    while (wrap.firstChild) parent.insertBefore(wrap.firstChild, wrap)
    parent.removeChild(wrap)
  })
}

let codeCopyInited = false

/**
 * 全局代理代码块“复制”按钮的点击（事件委托，正文重新渲染后无需重新绑定）：
 * 点击后复制该代码块的纯文本，成功时图标变对勾、提示变「已复制」。
 * 并用 MutationObserver 自动给正文里新出现的代码块补上按钮：
 * 任何页面（含以后新增的页面）只要用 .article-body 渲染正文，代码块 / 文本块 / 脚本块都会自动带上复制按钮。
 */
export function enableCodeCopy(): void {
  if (codeCopyInited) return
  codeCopyInited = true
  document.addEventListener('click', async (e: MouseEvent) => {
    const target = e.target as HTMLElement

    const copyBtn = target.closest('.code-copy-btn') as HTMLButtonElement | null
    if (!copyBtn) return
    e.preventDefault()
    const pre = copyBtn.closest('.code-block')?.querySelector('pre')
    const text = (pre?.querySelector('code') ?? pre)?.textContent ?? ''
    if (!text) return
    const ok = await copyText(text)
    copyBtn.innerHTML = ok ? ICON_COPIED : ICON_COPY
    copyBtn.classList.toggle('is-copied', ok)
    copyBtn.classList.toggle('is-failed', !ok)
    const tip = ok ? '已复制' : '复制失败'
    copyBtn.setAttribute('data-tip', tip)
    copyBtn.setAttribute('aria-label', tip)
    window.setTimeout(() => {
      copyBtn.innerHTML = ICON_COPY
      copyBtn.classList.remove('is-copied', 'is-failed')
      copyBtn.setAttribute('data-tip', '复制代码')
      copyBtn.setAttribute('aria-label', '复制代码')
    }, 1600)
  })

  let pending = false
  const scan = () => {
    pending = false
    document.querySelectorAll<HTMLElement>('.article-body').forEach((body) => enhanceCodeBlocks(body))
  }
  const schedule = () => {
    if (pending) return
    pending = true
    window.requestAnimationFrame(scan)
  }
  new MutationObserver((mutations) => {
    for (const m of mutations) {
      for (const node of Array.from(m.addedNodes)) {
        if (!(node instanceof HTMLElement)) continue
        // 只关心正文区域的变化：正文整体插入（含整页挂载）或正文里新增了内容
        if (node.closest('.article-body') || node.querySelector('.article-body')) {
          schedule()
          return
        }
      }
    }
  }).observe(document.body, { childList: true, subtree: true })
}

let mermaidInstance: unknown = null

async function loadMermaid(): Promise<any> {
  if (mermaidInstance) return mermaidInstance
  const mod = await import('mermaid')
  const mermaid = (mod as any).default
  // 站点主题：white 用浅色，其余（amber/black）用深色
  const theme = document.documentElement.getAttribute('data-theme') === 'white' ? 'default' : 'dark'
  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'strict',
    theme,
    // 用与站点一致的中文字体栈。mermaid 默认用 trebuchet/verdana/arial 测节点标签宽度，
    // 中文会回退到系统字体，可能与实际渲染宽度不一致，导致节点按“偏小尺寸”定框、
    // 真实中文标签溢出 <foreignObject> 被裁切（表现为字体显示不完整）。
    // 统一度量字体后，测量与渲染一致，节点尺寸更准确。
    themeVariables: {
      fontFamily: '"PingFang SC", "Microsoft YaHei", "Helvetica Neue", Arial, sans-serif'
    }
  })
  mermaidInstance = mermaid
  return mermaid
}

/**
 * 把正文里的 Mermaid / flowchart 代码块替换为渲染后的 SVG 图。
 * - 仅在存在 mermaid 代码块时动态加载 mermaid，避免打进主包。
 * - 渲染失败（语法/内容问题）时保留原代码块，不阻断其它内容。
 */
export async function renderDiagrams(container: HTMLElement | null): Promise<void> {
  if (!container) return
  // 常见 mermaid 指令首行，用于在服务端未输出 language 类时仍能识别
  const DIRECTIVE = /^(flowchart|graph|sequenceDiagram|classDiagram|stateDiagram|erDiagram|gantt|pie|journey|mindmap|gitGraph|quadrantChart|timeline|xychart|block|sankey|requirement|packet|c4)\b/i
  const blocks = Array.from(container.querySelectorAll<HTMLElement>('pre code')).filter((codeEl) => {
    if (/language-(mermaid|flowchart)/i.test(codeEl.className || '')) return true
    return DIRECTIVE.test((codeEl.textContent || '').trimStart())
  })
  if (!blocks.length) return
  let mermaid: any
  try {
    mermaid = await loadMermaid()
  } catch {
    // 加载失败：保持原样展示为代码块
    return
  }
  let seq = 0
  for (const codeEl of blocks) {
    const pre = codeEl.closest('pre')
    if (!pre) continue
    const source = (codeEl.textContent || '').trim()
    if (!source) continue
    const id = `mmd-${Date.now()}-${++seq}`
    try {
      const { svg } = await mermaid.render(id, source)
      const div = document.createElement('div')
      div.className = 'mermaid-render'
      div.innerHTML = svg
      // 强制 svg 随容器宽度自适应（svg 带 viewBox，会按比例缩放），避免被 mermaid 写死的固定宽度裁切
      const svgEl = div.querySelector('svg')
      if (svgEl) {
        svgEl.setAttribute('width', '100%')
        svgEl.style.maxWidth = '100%'
        svgEl.style.height = 'auto'
      }
      pre.replaceWith(div)
    } catch {
      // 单个图失败不阻断其它图，保留原代码块
    }
  }
}

let linkHandlerInited = false

/**
 * 全局处理正文（.article-body）里 Markdown 链接的点击，一处生效，覆盖文章详情、学习专栏、
 * 后台/投稿预览、用户上传详情等所有渲染正文的地方：
 * - 站内路由（以 / 开头的链接）：SPA 跳转（当前页切换，但不整页刷新）；
 * - 外链（http/https）：设置 target="_blank" + rel="noopener noreferrer"，交给浏览器原生行为在
 *   新标签页打开，当前页保持不变。不再用 window.open，避免它带 noopener 时返回 null，
 *   触发“新开标签页的同时当前页也被跳走”的问题；
 * - mailto / #锚点 / 其它协议：交给浏览器默认行为，不做拦截。
 */
export function enableBodyLinkHandler(router: Router): void {
  if (linkHandlerInited) return
  linkHandlerInited = true
  document.addEventListener('click', (e: MouseEvent) => {
    const target = (e.target as HTMLElement).closest('a') as HTMLAnchorElement | null
    if (!target) return
    if (!target.closest('.article-body')) return // 只处理正文内容区，不影响页头/导航/后台菜单
    const href = target.getAttribute('href') || ''
    if (!href) return
    if (href.startsWith('#')) return // 页内锚点：保留默认滚动
    if (href.startsWith('/')) {
      // 站内路由：SPA 跳转，不整页刷新
      e.preventDefault()
      router.push(href)
      return
    }
    if (/^(https?:)?\/\//i.test(href)) {
      // 外链：新标签页打开，当前页不变
      target.setAttribute('target', '_blank')
      target.setAttribute('rel', 'noopener noreferrer')
    }
  })
}
