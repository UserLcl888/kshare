<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="section-title">文件上传</h3>
      <span class="page-tip">上传后把链接复制到 Markdown 正文里，读者点开就是新标签页</span>
    </div>

    <div class="upload-row">
      <el-radio-group v-model="kind">
        <el-radio-button value="html">HTML 页面</el-radio-button>
        <el-radio-button value="file">文档文件</el-radio-button>
      </el-radio-group>

      <el-upload
        class="upload-box"
        drag
        :disabled="uploading"
        :accept="kind === 'html' ? '.html,.htm' : '.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.zip'"
        :show-file-list="false"
        :http-request="onUpload"
        :before-upload="beforeUpload"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-hint">把文件拖到这里，或 <em>点击选择</em></div>
        <div class="upload-sub">
          <template v-if="uploading">上传中，请稍候…</template>
          <template v-else>
            {{ kind === 'html'
              ? '单个 .html/.htm，最大 5MB；浏览器直接渲染，页面里的 JS 正常执行'
              : 'pdf / word / excel / ppt / txt / csv / zip，最大 50MB' }}
          </template>
        </div>
      </el-upload>
    </div>

    <div v-if="lastUpload" class="result-box">
      <div class="result-title">
        <el-icon><CircleCheck /></el-icon>
        <span>上传成功：{{ lastUpload.fileName }}</span>
      </div>
      <div class="result-row">
        <el-input :model-value="lastUpload.url" readonly />
        <el-button type="primary" @click="copy(lastUpload.url)">复制链接</el-button>
        <el-button @click="openUrl(lastUpload.url)">打开看看</el-button>
      </div>
      <div class="result-md">
        <span>正文里这样写：</span>
        <code>{{ mdSnippet }}</code>
        <el-button size="small" text type="primary" @click="copy(mdSnippet)">复制 Markdown</el-button>
      </div>
    </div>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="按文件名搜索"
        clearable
        class="toolbar-input"
        @keyup.enter="reload"
        @clear="reload"
      />
      <el-select v-model="filterKind" placeholder="全部类型" clearable class="toolbar-select" @change="reload">
        <el-option label="HTML 页面" value="html" />
        <el-option label="文档" value="file" />
      </el-select>
      <el-button @click="reload">查询</el-button>
    </div>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="fileName" label="文件名" width="280" show-overflow-tooltip />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" effect="plain" :type="row.kind === 'html' ? 'primary' : 'warning'">
            {{ row.kind === 'html' ? 'HTML 页面' : '文档' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="110">
        <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="190">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="链接" min-width="260" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="url-text">{{ row.url }}</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="250" fixed="right" class-name="asset-op-col">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="copy(row.url)">复制链接</el-button>
          <el-button size="small" text type="primary" @click="copyMarkdown(row)">复制 MD</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !list.length" description="还没有上传过文件" />

    <div v-if="total > size" class="pager">
      <el-pagination
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        @current-change="onPageChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleCheck, UploadFilled } from '@element-plus/icons-vue'
import { deleteAssetApi, getAssetsApi, uploadAssetApi, type AssetItem } from '@/api/admin'
import { formatDateTime } from '@/utils/format'

const kind = ref<'html' | 'file'>('html')
const uploading = ref(false)
const loading = ref(false)
const list = ref<AssetItem[]>([])
const lastUpload = ref<AssetItem | null>(null)
const keyword = ref('')
const filterKind = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)

const mdSnippet = computed(() =>
  lastUpload.value ? `[点我打开](${lastUpload.value.url})` : ''
)

function beforeUpload(file: File) {
  const name = (file.name || '').toLowerCase()
  if (kind.value === 'html') {
    if (!/\.html?$/.test(name)) {
      ElMessage.warning('HTML 页面仅支持 .html/.htm 文件')
      return false
    }
    if (file.size > 5 * 1024 * 1024) {
      ElMessage.warning('HTML 文件不能超过 5MB')
      return false
    }
  } else {
    if (!/\.(pdf|docx?|xlsx?|pptx?|txt|csv|zip)$/.test(name)) {
      ElMessage.warning('仅支持 pdf / word / excel / ppt / txt / csv / zip')
      return false
    }
    if (file.size > 50 * 1024 * 1024) {
      ElMessage.warning('文件不能超过 50MB')
      return false
    }
  }
  return true
}

async function onUpload(options: {
  file: File
  onSuccess: (res: unknown) => void
  onError: (err: Error) => void
}) {
  uploading.value = true
  try {
    const asset = await uploadAssetApi(options.file, kind.value)
    lastUpload.value = asset
    options.onSuccess(asset)
    ElMessage.success('上传成功，链接已生成')
    page.value = 1
    await load()
  } catch (e) {
    options.onError(e as Error)
  } finally {
    uploading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getAssetsApi({
      keyword: keyword.value.trim() || undefined,
      kind: filterKind.value || undefined,
      page: page.value,
      size: size.value
    })
    list.value = res.list
    total.value = res.total
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  load()
}

function onPageChange(p: number) {
  page.value = p
  load()
}

async function remove(row: AssetItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除「${row.fileName}」吗？文件会从对象存储里一并删掉，已经引用了这个链接的正文会变成死链。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteAssetApi(row.id)
    ElMessage.success('删除成功')
    if (lastUpload.value?.id === row.id) lastUpload.value = null
    load()
  } catch {
    // 拦截器已提示
  }
}

function copyMarkdown(row: AssetItem) {
  copy(`[点我打开](${row.url})`)
}

function openUrl(url: string) {
  window.open(url, '_blank', 'noopener')
}

/** 复制到剪贴板：http 环境下 Clipboard API 不可用，退回 textarea + execCommand */
async function copy(text: string) {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
    } else {
      const ta = document.createElement('textarea')
      ta.value = text
      ta.style.position = 'fixed'
      ta.style.opacity = '0'
      document.body.appendChild(ta)
      ta.select()
      document.execCommand('copy')
      ta.remove()
    }
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选中复制')
  }
}

function formatFileSize(bytes: number): string {
  if (!bytes) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

onMounted(load)
</script>

<style scoped>
.page-card {
  background: var(--app-card);
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 18px 20px;
}

.page-header {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  color: var(--app-title-accent);
}

.page-tip {
  font-size: 12px;
  color: var(--app-text-secondary);
}

.upload-row {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  flex-wrap: wrap;
}

.upload-box {
  flex: 1;
  min-width: 320px;
}

.upload-box :deep(.el-upload-dragger) {
  padding: 22px 16px;
  background: var(--app-card-translucent);
  border-color: var(--app-border);
}

.upload-icon {
  font-size: 32px;
  color: var(--app-text-secondary);
}

.upload-hint {
  margin-top: 6px;
  font-size: 14px;
  color: var(--app-text);
}

.upload-sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.result-box {
  margin: 18px 0 6px;
  padding: 14px 16px;
  border: 1px solid var(--app-accent);
  border-radius: 10px;
  background: var(--app-accent-soft);
}

.result-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--app-text);
  margin-bottom: 10px;
}

.result-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.result-md {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 13px;
  color: var(--app-text-secondary);
  flex-wrap: wrap;
}

.result-md code {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--app-card);
  border: 1px solid var(--app-border);
  color: var(--app-text);
  word-break: break-all;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 18px 0 12px;
}

.toolbar-input {
  width: 220px;
}

.toolbar-select {
  width: 150px;
}

.url-text {
  font-size: 12px;
  color: var(--app-text-secondary);
  word-break: break-all;
}

/* 操作列：三个按钮固定一行，不因为列宽不够换行 */
:deep(.asset-op-col .cell) {
  display: flex;
  align-items: center;
  white-space: nowrap;
  overflow: visible;
}

:deep(.asset-op-col .el-button) {
  padding: 4px 6px;
}

:deep(.asset-op-col .el-button + .el-button) {
  margin-left: 6px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
