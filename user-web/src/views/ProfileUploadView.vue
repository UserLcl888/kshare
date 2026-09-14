<template>
  <div class="page">
    <div class="page-body">
      <main class="content">
        <el-breadcrumb class="breadcrumb-bar" separator="/">
          <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item :to="{ path: '/profile' }">个人中心</el-breadcrumb-item>
          <el-breadcrumb-item>内容上传</el-breadcrumb-item>
        </el-breadcrumb>

        <div class="app-card">
          <h3 class="section-title">上传内容（Markdown）</h3>
          <el-form label-width="100px" class="upload-form" @submit.prevent>
            <el-form-item label="标题" required>
              <el-input v-model="form.title" placeholder="请输入内容标题，如：我的 Redis 面试总结" maxlength="200" />
            </el-form-item>

            <el-form-item label="主题分类" required>
              <div class="category-field">
                <el-select v-model="form.categoryMode" placeholder="选择已有主题，或自定义" style="flex: 1">
                  <el-option v-for="cat in categoryOptions" :key="cat.id" :label="cat.name" :value="String(cat.id)" />
                  <el-option label="自定义主题" value="custom" />
                </el-select>
                <el-input
                  v-if="form.categoryMode === 'custom'"
                  v-model="form.customCategory"
                  placeholder="输入你的自定义主题，如：架构设计心得"
                  style="flex: 1"
                />
              </div>
            </el-form-item>

            <el-form-item label="分组" v-if="form.categoryMode && form.categoryMode !== 'custom'">
              <div class="category-field">
                <el-select v-model="form.groupMode" placeholder="选择分组（可不选）" clearable style="flex: 1">
                  <el-option v-for="sub in groupOptions" :key="sub.id" :label="sub.name" :value="String(sub.id)" />
                  <el-option label="自定义分组" value="custom" />
                </el-select>
                <el-input
                  v-if="form.groupMode === 'custom'"
                  v-model="form.customGroup"
                  placeholder="输入你的自定义分组"
                  style="flex: 1"
                />
              </div>
            </el-form-item>

            <el-form-item label="分组" v-else-if="form.categoryMode === 'custom'">
              <el-input v-model="form.customGroup" placeholder="自定义分组（选填），如：一面记录" />
            </el-form-item>

            <el-form-item label="MD 文件" required>
              <el-upload
                drag
                accept=".md,.markdown"
                :auto-upload="false"
                :limit="1"
                :show-file-list="false"
                :file-list="mdFileList"
                :on-change="onMdFileChange"
                :on-exceed="onMdExceed"
              >
                <div class="upload-hint">
                  <template v-if="!selectedRawFile">
                    <el-icon class="upload-icon"><UploadFilled /></el-icon>
                    <div>拖拽 .md 文件到此处，或 <em>点击选择</em></div>
                    <div class="upload-tip">仅支持 .md / .markdown；每个账号每天最多提交 <b>3 篇</b></div>
                    <div class="upload-tip" :class="{ 'upload-tip--warn': quota.remaining === 0 }">
                      {{ quota.remaining > 0 ? `今天还可以提交 ${quota.remaining} 篇` : '今天提交次数已达上限（每天 3 篇），请明天再试' }}
                    </div>
                    <div class="upload-tip upload-tip--warn">
                      图片请用网络链接；本地图片可直接拖进正文粘贴上传（相对路径的图片无法读取，会显示不出）
                    </div>
                  </template>
                  <template v-else>
                    <div class="picked-file">
                      <span class="picked-icon">✅</span>
                      <div class="picked-body">
                        <div class="picked-name">{{ selectedRawFile.name }}</div>
                        <div class="picked-size">{{ (selectedRawFile.size / 1024).toFixed(1) }} KB</div>
                      </div>
                      <el-button link type="danger" size="small" @click.stop="clearMdFile">重新选择</el-button>
                    </div>
                    <!-- 进度和文件卡片一起显示在框内 -->
                    <div v-if="progress.visible" class="upload-progress">
                      <el-progress
                        :percentage="progress.percent"
                        :status="progress.failed ? 'exception' : undefined"
                        :stroke-width="10"
                      />
                      <div class="upload-progress-text">{{ progress.text }}</div>
                    </div>
                    <div v-else class="upload-tip">文件已选择，点下方按钮提交</div>
                  </template>
                </div>
              </el-upload>
            </el-form-item>

            <el-form-item>
              <el-button native-type="button" :disabled="!mdContent" size="small" @click="previewVisible = true">预览效果</el-button>
              <el-button native-type="button" type="primary" :loading="saving" @click="submit">提交上传</el-button>
            </el-form-item>
          </el-form>
        </div>

        <div class="app-card my-list-card">
          <h3 class="section-title">我的上传</h3>
          <el-table :data="list" stripe row-key="id" :expand-row-keys="expandedKeys">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="expand-row">
                  <div class="expand-label">管理员回复（{{ row.adminReply ? formatDateTime(row.repliedAt || '') : '暂无回复' }}）</div>
                  <div v-if="row.adminReply" class="expand-text">{{ row.adminReply }}</div>
                  <div v-else class="expand-empty">暂未回复，请耐心等待</div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
            <el-table-column label="主题 / 分组" min-width="160">
              <template #default="{ row }">
                {{ row.categoryName }}<template v-if="row.groupName"> / {{ row.groupName }}</template>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.processStatus === 0" type="info" size="small">处理中</el-tag>
                <el-tag v-else-if="row.processStatus === 2" type="danger" size="small">处理失败</el-tag>
                <el-tag v-else :type="row.adminReply ? 'success' : 'warning'" size="small">
                  {{ row.adminReply ? '已回复' : '待处理' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="提交时间" min-width="160">
              <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="140">
              <template #default="{ row }">
                <el-button size="small" text type="primary" @click="openDetail(row.id)">查看</el-button>
                <el-button size="small" text type="danger" @click="removeUpload(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!loading && !list.length" class="empty-tip">暂无上传记录</div>
          <div class="pager">
            <el-pagination
              layout="total, prev, pager, next"
              :total="total"
              :page-size="query.size"
              :current-page="query.page"
              @current-change="onPage"
            />
          </div>
        </div>

        <el-dialog
          v-model="previewVisible"
          title="Markdown 预览"
          width="min(1120px, 94vw)"
          top="6vh"
          append-to-body
          @open="onPreviewOpen"
        >
          <div class="preview-layout">
            <div ref="previewRoot" class="preview-main">
              <div v-if="previewLoading" class="preview-loading">渲染中…</div>
              <div v-else ref="previewBody" class="article-body preview-body" v-html="previewHtml"></div>
            </div>
            <TocPanel :toc="previewToc" :scroll-root="previewRoot" />
          </div>
          <div class="preview-tip">预览效果与审核通过后发布的正文一致（代码高亮、Mermaid 流程图、目录跳转）。</div>
        </el-dialog>

        <el-dialog v-model="detailVisible" title="上传详情" width="860px" top="6vh" append-to-body>
          <template v-if="detail">
            <el-descriptions :column="2" border size="small" class="detail-desc">
              <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
              <el-descriptions-item label="主题">{{ detail.categoryName }}</el-descriptions-item>
              <el-descriptions-item label="分组">{{ detail.groupName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="detail.adminReply ? 'success' : 'warning'" size="small">
                  {{ detail.adminReply ? '已回复' : '待处理' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="文件名">{{ detail.fileName }}</el-descriptions-item>
              <el-descriptions-item label="提交时间">{{ formatDateTime(detail.createdAt) }}</el-descriptions-item>
            </el-descriptions>

            <el-tabs v-model="detailTab">
              <el-tab-pane label="预览效果" name="preview">
                <div class="preview-layout">
                  <div ref="detailRoot" class="preview-main detail-scroll">
                    <div ref="detailPreview" class="article-body detail-md" v-html="detailHtml"></div>
                  </div>
                  <TocPanel :toc="detailToc" :scroll-root="detailRoot" />
                </div>
              </el-tab-pane>
              <el-tab-pane label="原文" name="raw">
                <pre class="raw-md">{{ detail.contentMd }}</pre>
              </el-tab-pane>
            </el-tabs>
          </template>
        </el-dialog>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type UploadFile, type UploadUserFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { useCategoryStore } from '@/stores/category'
import { createUserUploadApi, deleteMyUploadApi, getMyUploadDetailApi, getMyUploadsApi } from '@/api/upload'
import request from '@/api/request'
import {
  collectToc,
  enhanceCodeBlocks,
  highlightCodeBlocks,
  renderDiagrams,
  renderMarkdown,
  renderMarkdownWithToc
} from '@/utils/markdown'
import { previewUploadMdApi } from '@/api/upload'
import TocPanel from '@/components/article/TocPanel.vue'
import { readFileAsText } from '@/utils/file'
import { formatDateTime } from '@/utils/format'
import type { TocItem, UserUploadDetail, UserUploadItem } from '@/types'

const categoryStore = useCategoryStore()
const route = useRoute()
const router = useRouter()
const categoryOptions = computed(() => categoryStore.tree)

/**
 * 表单状态（含 md 内容）保存到 sessionStorage：
 * 刷新页面不丢；离开页面会先提示「放弃并离开 / 继续编辑」，放弃则连草稿一起清掉，
 * 所以下次进这个页面不会再冒出上一次选好但没提交的 md 文件。
 */
const STORAGE_KEY = 'profile-upload-form'

const form = reactive({
  title: '',
  categoryMode: '',
  customCategory: '',
  groupMode: '',
  customGroup: ''
})

const selectedCategory = computed(
  () => categoryOptions.value.find((c) => String(c.id) === form.categoryMode) || null
)
const groupOptions = computed(() => selectedCategory.value?.children || [])

const categoryName = computed(() => {
  if (form.categoryMode === 'custom') return form.customCategory.trim()
  return selectedCategory.value?.name || ''
})

const groupName = computed(() => {
  if (form.groupMode === 'custom') return form.customGroup.trim()
  const sub = groupOptions.value.find((s) => String(s.id) === form.groupMode)
  return sub?.name || ''
})

watch(
  () => form.categoryMode,
  () => {
    form.groupMode = ''
    form.customGroup = ''
  }
)

const mdFileList = ref<UploadUserFile[]>([])
const selectedRawFile = ref<File | null>(null)
/** 提交后的处理进度：0→90% 匀速模拟，后台处理完成跳 100% */
const progress = reactive({ visible: false, percent: 0, text: '', failed: false })
let progressTimer: number | null = null
let processPollTimer: number | null = null

function stopProgressTimers() {
  if (progressTimer !== null) { window.clearInterval(progressTimer); progressTimer = null }
  if (processPollTimer !== null) { window.clearInterval(processPollTimer); processPollTimer = null }
}

/** 取消已选文件，允许重新选择（连已读入的正文一起清掉，避免草稿/预览里残留上一份内容） */
function clearMdFile() {
  mdFileList.value = []
  selectedRawFile.value = null
  mdContent.value = ''
}

/** 轮询这条投稿的处理状态：1=已完成 2=处理失败 */
function waitProcess(id: number) {
  let times = 0
  processPollTimer = window.setInterval(async () => {
    times += 1
    try {
      const res = await getMyUploadsApi({ page: 1, size: 20 })
      // processStatus 由后端异步写入，这里按任意字段读取
      const row = res.list.find((x) => x.id === id) as unknown as
        | { processStatus?: number }
        | undefined
      if (row && row.processStatus === 1) {
        stopProgressTimers()
        progress.percent = 100
        progress.text = '处理完成'
        ElMessage.success('投稿处理完成，等待管理员审核')
        load()
        // 处理完成后把上传框恢复成初始状态（文件卡片和进度一起消失）
        window.setTimeout(() => {
          progress.visible = false
          clearMdFile()
          draftMuted = false
        }, 1500)
      } else if (row && row.processStatus === 2) {
        stopProgressTimers()
        progress.failed = true
        progress.text = '处理失败，请重新提交'
        ElMessage.error('投稿处理失败，请检查图片链接后重试')
        load()
      }
    } catch {
      // 轮询失败不打断，等下一轮
    }
    if (times > 80) stopProgressTimers()
  }, 1500)
}
/** 今日投稿额度（每天 3 篇），用于"今天还可以提交 N 篇"提示 */
const quota = reactive({ limit: 3, used: 0, remaining: 3 })

async function loadQuota() {
  try {
    const data = await request.get('/user/uploads/quota')
    if (data) {
      quota.limit = data.limit ?? 3
      quota.used = data.used ?? 0
      quota.remaining = data.remaining ?? 0
    }
  } catch {
    // 取不到额度时不影响正常提交
  }
}
const mdContent = ref('')
const saving = ref(false)
const previewVisible = ref(false)
const previewBody = ref<HTMLElement | null>(null)
const previewRoot = ref<HTMLElement | null>(null)
const previewHtml = ref('')
const previewToc = ref<TocItem[]>([])
const previewLoading = ref(false)
/** 走服务端的正文长度上限：超过就本地渲染，避免一次提交几十 MB */
const PREVIEW_SERVER_LIMIT = 2_000_000

function snapshotForm(): string {
  return JSON.stringify({
    title: form.title,
    categoryMode: form.categoryMode,
    customCategory: form.customCategory,
    groupMode: form.groupMode,
    customGroup: form.customGroup,
    mdContent: mdContent.value
  })
}

/** 草稿写入延时：避免每敲一个字就把整份 md 重写一遍 sessionStorage */
const DRAFT_DELAY = 400
/**
 * 已经提交成功的那份内容：文件卡片还留在框里是为了继续显示处理进度，
 * 但它已经不是草稿了 —— 期间不写草稿并清掉旧草稿，
 * 否则离开后再进来又会把上一篇的 md 恢复出来。用户重新选文件时解除。
 */
let draftMuted = false
let draftTimer: number | null = null

/** 表单里是否有还没提交的内容（有的话离开页面要提醒一次） */
const hasDraftContent = computed(() =>
  Boolean(mdContent.value.trim() || form.title.trim() || categoryName.value || groupName.value)
)

function clearDraftStorage() {
  if (draftTimer !== null) {
    window.clearTimeout(draftTimer)
    draftTimer = null
  }
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // 忽略
  }
}

watch(snapshotForm, (snapshot) => {
  // 已提交、或表单已经空了：把草稿删掉，不留空快照也不留旧内容
  if (draftMuted || !hasDraftContent.value) {
    clearDraftStorage()
    return
  }
  if (draftTimer !== null) window.clearTimeout(draftTimer)
  draftTimer = window.setTimeout(() => {
    draftTimer = null
    try {
      sessionStorage.setItem(STORAGE_KEY, snapshot)
    } catch {
      // 内容过大超出存储配额时放弃持久化，不影响正常上传
    }
  }, DRAFT_DELAY)
})

function restoreForm() {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw) as Record<string, string>
    if (saved.title) form.title = saved.title
    if (saved.categoryMode) form.categoryMode = saved.categoryMode
    if (saved.customCategory) form.customCategory = saved.customCategory
    if (saved.groupMode) form.groupMode = saved.groupMode
    if (saved.customGroup) form.customGroup = saved.customGroup
    if (saved.mdContent) {
      mdContent.value = saved.mdContent
      const fileName = 'restored.md'
      mdFileList.value = [{ name: fileName, status: 'ready' }] as UploadUserFile[]
      selectedRawFile.value = new File([saved.mdContent], fileName, { type: 'text/markdown' })
    }
  } catch {
    // 恢复失败时保持空表单
  }
}

/** 放弃草稿：清掉存储 + 清空表单，保证下次进这个页面是干净的空表单 */
function discardDraft() {
  draftMuted = false
  clearDraftStorage()
  resetForm()
}

/** 离开提示文案：选了文件就点名说清楚；只填过内容时也讲明白 */
function draftLeaveMessage(): string {
  const file = selectedRawFile.value
  if (file) {
    return `你选择的「${file.name}」还没有提交。离开后需要重新选择文件，已填写的标题、分类也会一并清空。`
  }
  return '已填写的标题、分类等内容还没有提交，离开后不会保留。'
}

/**
 * 离开这个页面前拦一次：选好的 md 文件（或填过的标题、分类）还没提交时，
 * 提示「放弃并离开 / 继续编辑」—— 选放弃才放行，并且顺手清掉草稿，下次进来就是空表单。
 * 登录态失效跳登录时不再打扰（草稿留着，登录回来还能接着填）。
 */
onBeforeRouteLeave(async (to) => {
  if (to.name === 'login') return true
  if (draftMuted || !hasDraftContent.value) return true
  try {
    await ElMessageBox.confirm(draftLeaveMessage(), '这篇投稿还没提交', {
      confirmButtonText: '放弃并离开',
      cancelButtonText: '继续编辑',
      type: 'warning'
    })
  } catch {
    // 取消 / 关闭弹窗：留在当前页面继续编辑
    return false
  }
  discardDraft()
  return true
})

function onMdExceed() {
  ElMessage.warning('一次只能上传一个文件，请先点「重新选择」清空后再选')
}

async function onMdFileChange(file: UploadFile, files: UploadUserFile[]) {
  // 已有文件时不允许再选第二个：必须先「重新选择」
  if (selectedRawFile.value) {
    ElMessage.warning('一次只能上传一个文件，请先点「重新选择」清空后再选')
    return
  }
  mdFileList.value = files.slice(-1)
  const raw = file.raw
  if (!raw) return
  if (raw.size > 3 * 1024 * 1024) {
    ElMessage.warning('文件超过大小限制，请压缩或去掉内嵌大图后重试')
    return
  }
  try {
    mdContent.value = await readFileAsText(raw)
    selectedRawFile.value = raw
    // 选了新文件 = 一份新的、还没提交的内容：恢复草稿写入（上一份已提交的不再算草稿）
    draftMuted = false
  } catch {
    // 读取失败忽略
  }
}

/**
 * 预览：优先用服务端渲染（和入库正文同一套解析 + 消毒规则），
 * 接口异常或内容过大时退回本地渲染（规则已与后端对齐）。
 */
async function onPreviewOpen() {
  const md = mdContent.value
  previewLoading.value = true
  previewHtml.value = ''
  previewToc.value = []
  try {
    if (md.length > PREVIEW_SERVER_LIMIT) throw new Error('内容过长，改用本地渲染')
    const res = await previewUploadMdApi(md)
    previewHtml.value = res.contentHtml || ''
    previewToc.value = res.toc || []
  } catch {
    const local = renderMarkdownWithToc(md)
    previewHtml.value = local.html
    previewToc.value = local.toc
  } finally {
    previewLoading.value = false
  }
  await nextTick()
  if (!previewBody.value) return
  highlightCodeBlocks(previewBody.value)
  await renderDiagrams(previewBody.value)
  enhanceCodeBlocks(previewBody.value)
  if (!previewToc.value.length) previewToc.value = collectToc(previewBody.value)
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写标题')
    return
  }
  if (!categoryName.value) {
    ElMessage.warning('请选择或填写主题分类')
    return
  }
  if (!selectedRawFile.value || !mdContent.value.trim()) {
    ElMessage.warning('请选择并读取 Markdown 文件')
    return
  }
  saving.value = true
  try {
    const fd = new FormData()
    fd.append('title', form.title.trim())
    fd.append('categoryName', categoryName.value)
    if (groupName.value) fd.append('groupName', groupName.value)
    fd.append('file', selectedRawFile.value)
    // ① 先把进度显示出来（上传请求本身也要时间），0→90% 匀速模拟，后台完成后轮询跳 100%
    stopProgressTimers()
    progress.visible = true
    progress.failed = false
    progress.percent = 5
    progress.text = '正文图片正在搬入图床，请稍候…'
    progressTimer = window.setInterval(() => {
      if (progress.percent < 90) progress.percent += 2
    }, 400)
    const created = await createUserUploadApi(fd)
    ElMessage.success('投稿已提交，正在处理中…')
    if (created?.id) waitProcess(created.id)
    loadQuota()
    // ② 这份内容已经提交出去了：不再是草稿（文件卡片留着继续显示处理进度）
    draftMuted = true
    clearDraftStorage()
    // 只清标题，保留文件卡片与进度（进度要继续显示在框里，处理完成后才自动复位）
    form.title = ''
    reload()
  } finally {
    saving.value = false
  }
}

function resetForm() {
  form.title = ''
  form.categoryMode = ''
  form.customCategory = ''
  form.groupMode = ''
  form.customGroup = ''
  mdFileList.value = []
  selectedRawFile.value = null
  mdContent.value = ''
}

const list = ref<UserUploadItem[]>([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ page: 1, size: 10 })
const expandedKeys = ref<number[]>([])

async function load() {
  loading.value = true
  try {
    const res = await getMyUploadsApi({ page: query.page, size: query.size })
    list.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

/** 静默轮询：管理员回复后，用户端无需刷新即可看到最新状态 */
let pollTimer: number | null = null

async function refreshSilently() {
  try {
    const res = await getMyUploadsApi({ page: query.page, size: query.size })
    total.value = res.total
    const byId = new Map(list.value.map((x) => [x.id, x]))
    for (const item of res.list) {
      const exist = byId.get(item.id)
      if (exist) {
        Object.assign(exist, item)
      } else {
        list.value.push(item)
      }
    }
    if (detail.value?.id) {
      const fresh = await getMyUploadDetailApi(detail.value.id)
      detail.value = fresh
    }
  } catch {
    // 轮询失败静默忽略，不打扰用户
  }
}

function reload() {
  query.page = 1
  load()
}

function onPage(p: number) {
  query.page = p
  load()
}

async function removeUpload(row: UserUploadItem) {
  try {
    await ElMessageBox.confirm(`确定删除上传内容“${row.title}”吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteMyUploadApi(row.id)
    if (detail.value?.id === row.id) detailVisible.value = false
    ElMessage.success('删除成功')
    reload()
  } catch {
    // 拦截器已提示
  }
}

/** 从通知点击跳转过来时，自动展开对应上传；不在当前页则打开详情 */
async function handleJump() {
  const id = Number(route.query.upload || 0)
  if (!id) return
  if (!list.value.length) await load()
  const row = list.value.find((x) => x.id === id)
  if (row) {
    expandedKeys.value = [id]
  } else {
    try {
      detail.value = await getMyUploadDetailApi(id)
      detailVisible.value = true
    } catch {
      // 已删除或无权限，忽略
    }
  }
  router.replace({ query: {} })
}

const detailVisible = ref(false)
const detail = ref<UserUploadDetail | null>(null)
const detailTab = ref('preview')
const detailPreview = ref<HTMLElement | null>(null)
const detailRoot = ref<HTMLElement | null>(null)
const detailToc = ref<TocItem[]>([])

/**
 * 上传详情正文：已处理完的用服务端 HTML；
 * 还在异步处理（processStatus=0）或处理失败时 contentHtml 为空，退回本地渲染原文，避免预览空白。
 */
const detailHtml = computed(() => {
  if (!detail.value) return ''
  return detail.value.contentHtml || renderMarkdown(detail.value.contentMd || '')
})

async function openDetail(id: number) {
  detailTab.value = 'preview'
  detail.value = await getMyUploadDetailApi(id)
  detailVisible.value = true
  await nextTick()
  highlightCodeBlocks(detailPreview.value)
  await renderDiagrams(detailPreview.value)
  enhanceCodeBlocks(detailPreview.value)
  detailToc.value = collectToc(detailPreview.value)
}

onMounted(async () => {
  loadQuota()
  restoreForm()
  await categoryStore.fetchTree()
  await load()
  await handleJump()
  pollTimer = window.setInterval(refreshSilently, 5000)
})

onBeforeUnmount(() => {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.page {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.page-body {
  flex: 1;
  width: 100%;
  padding: 16px 24px;
  display: flex;
  justify-content: center;
}

.content {
  width: 100%;
  max-width: 960px;
}

.section-title {
  margin: 0 0 16px;
  color: var(--app-title-accent);
}

.upload-form {
  width: 100%;
}

.category-field {
  display: flex;
  gap: 8px;
  width: 100%;
}

.upload-hint {
  text-align: center;
  padding: 8px 0;
}

.upload-icon {
  font-size: 36px;
  color: var(--app-text-secondary);
  margin-bottom: 6px;
}

/* 投稿图片提示：语气稍强，提醒图片引用方式 */
.picked-file {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: 8px;
  background: var(--app-accent-soft);
  font-size: 13px;
  width: 100%;
  max-width: 420px;
  animation: picked-in 0.28s cubic-bezier(0.22, 1, 0.36, 1);
}

.picked-body {
  flex: 1;
  min-width: 0;
  text-align: left;
}

.picked-icon {
  animation: picked-pop 0.45s ease;
}

@keyframes picked-in {
  from { opacity: 0; transform: translateY(6px); }
  to   { opacity: 1; transform: translateY(0); }
}

@keyframes picked-pop {
  0%   { transform: scale(0.6); }
  60%  { transform: scale(1.15); }
  100% { transform: scale(1); }
}

.picked-name {
  color: var(--app-text);
  font-weight: 600;
}

.picked-size {
  color: var(--app-text-secondary);
}

.upload-progress {
  margin-top: 12px;
  padding: 12px 14px;
  border-radius: 10px;
  background: var(--app-card);
  border: 1px solid var(--app-border);
}

.upload-progress-text {
  margin-top: 6px;
  font-size: 13px;
  color: var(--app-text-secondary);
  text-align: center;
}

.upload-tip--warn {
  color: var(--app-accent);
  opacity: 0.92;
}

.upload-tip {
  margin-top: 6px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.my-list-card {
  margin-top: 18px;
}

.expand-row {
  padding: 6px 12px;
}

.expand-label {
  font-size: 12px;
  color: var(--app-text-secondary);
  margin-bottom: 4px;
}

.expand-text {
  font-size: 13px;
  color: var(--app-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.expand-empty {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.empty-tip {
  padding: 18px 0;
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.preview-body {
  background: var(--app-card);
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 20px 24px;
}

.detail-desc {
  margin-bottom: 14px;
}

.reply-box {
  margin-bottom: 14px;
}

.reply-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.reply-content {
  white-space: pre-wrap;
}

.detail-md {
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 16px 20px;
}

/* 预览弹窗/详情里的「正文 + 右侧目录」两栏 */
.preview-layout {
  display: flex;
  align-items: stretch;
  gap: 16px;
}

.preview-main {
  flex: 1;
  min-width: 0;
  max-height: 68vh;
  overflow-y: auto;
}

.detail-scroll {
  max-height: 58vh;
}

.preview-loading {
  padding: 90px 0;
  text-align: center;
  font-size: 14px;
  color: var(--app-text-secondary);
}

.preview-tip {
  margin-top: 10px;
  text-align: center;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.raw-md {
  max-height: 60vh;
  overflow: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 10px;
  padding: 16px 18px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
