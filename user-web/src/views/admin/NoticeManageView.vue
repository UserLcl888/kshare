<template>
  <div class="page-card">
    <div class="page-header">
      <h3 class="section-title">公告管理</h3>
      <el-button type="primary" size="small" @click="openCreate">新增公告</el-button>
    </div>

    <el-table :data="list" stripe>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="150" />
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" text type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="!loading && !list.length" class="empty-tip">暂无公告，可直接新增</div>

    <el-dialog v-model="visible" :title="isEdit ? '编辑公告' : '新增公告'" width="480px" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="内容" required>
          <el-input v-model="form.content" type="textarea" :rows="2" maxlength="255" show-word-limit placeholder="如：网站维护中，卡顿请见谅" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="停用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createNoticeApi, deleteNoticeApi, getAdminNoticesApi, updateNoticeApi } from '@/api/notice'
import { useDraftStorage } from '@/composables/useDraft'
import type { NoticeItem } from '@/types'

const list = ref<NoticeItem[]>([])
const loading = ref(false)
const visible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editId = ref(0)
const form = reactive({ content: '', sortOrder: 0, status: 1 })

// 公告内容的输入先存草稿（sessionStorage，关标签页即清空；提交/取消也会立刻清掉）
const draft = useDraftStorage({
  getKey: () => 'draft:admin:notice',
  getSnapshot: () =>
    JSON.stringify({ content: form.content, sortOrder: form.sortOrder, status: form.status }),
  restore: (raw) => {
    try {
      const s = JSON.parse(raw) as { content?: string; sortOrder?: number; status?: number }
      if (typeof s.content === 'string') form.content = s.content
      if (typeof s.sortOrder === 'number') form.sortOrder = s.sortOrder
      form.status = s.status === 0 ? 0 : 1
    } catch {
      // 草稿损坏时忽略
    }
  }
})

watch(visible, (v) => {
  if (!v) draft.clear()
})

async function load() {
  loading.value = true
  try {
    list.value = await getAdminNoticesApi()
  } catch {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEdit.value = false
  editId.value = 0
  form.content = ''
  form.sortOrder = 0
  form.status = 1
  visible.value = true
}

function openEdit(row: NoticeItem) {
  isEdit.value = true
  editId.value = row.id
  form.content = row.content
  form.sortOrder = row.sortOrder || 0
  form.status = row.status === 1 ? 1 : 0
  visible.value = true
}

async function submit() {
  if (!form.content.trim()) {
    ElMessage.warning('请输入公告内容')
    return
  }
  saving.value = true
  try {
    const payload = {
      content: form.content.trim(),
      sortOrder: form.sortOrder,
      status: form.status
    }
    if (isEdit.value) {
      await updateNoticeApi(editId.value, payload)
      ElMessage.success('保存成功')
    } else {
      await createNoticeApi(payload)
      ElMessage.success('新增成功')
    }
    draft.clear()
    visible.value = false
    load()
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

async function remove(row: NoticeItem) {
  try {
    await ElMessageBox.confirm(`确定删除公告“${row.content}”吗？删除后前台不再显示。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  try {
    await deleteNoticeApi(row.id)
    ElMessage.success('删除成功')
    load()
  } catch {
    // 拦截器已提示
  }
}

onMounted(() => {
  load()
  if (draft.restoreNow()) {
    visible.value = true
    ElMessage.info('已恢复上次未保存的输入')
  }
})
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.section-title {
  margin: 0;
  color: var(--app-title-accent);
}

.empty-tip {
  padding: 18px 0;
  text-align: center;
  color: var(--app-text-secondary);
  font-size: 13px;
}
</style>
